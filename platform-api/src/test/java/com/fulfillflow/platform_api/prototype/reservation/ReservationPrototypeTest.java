package com.fulfillflow.platform_api.prototype.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PROTOTYP DO WYRZUCENIA (branch prototype/stock-locking).
 *
 * Pytanie: czy warunkowy UPDATE z PRD 6.1 / ADR 0001 naprawdę zapobiega overbookingowi
 * i czy sortowanie pozycji po ID produktu usuwa deadlocki?
 *
 * Celowo bez Springa, JPA i puli połączeń: każdy wątek ma własne połączenie JDBC,
 * więc wątki ścigają się o wiersz w bazie, a nie o miejsce w puli.
 */
class ReservationPrototypeTest {

	static final int RUNS = 20;
	static final int THREADS = 100;
	static final long X = 1, Y = 2;

	static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17-alpine")
			.withCommand("postgres", "-c", "max_connections=250");

	@BeforeAll
	static void startDb() throws SQLException {
		PG.start();
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute("""
					CREATE TABLE stock_level (
					    product_id BIGINT PRIMARY KEY,
					    on_hand    INT NOT NULL,
					    reserved   INT NOT NULL DEFAULT 0,
					    CHECK (reserved >= 0 AND reserved <= on_hand)
					)""");
		}
	}

	@AfterAll
	static void stopDb() {
		PG.stop();
	}

	@BeforeEach
	void cleanTable() throws SQLException {
		exec("TRUNCATE stock_level");
	}

	// ---------------------------------------------------------------- mechanizmy rezerwacji

	/** PRD 6.1: sprawdzenie i zapis w jednym kroku w bazie. */
	static boolean reserveConditional(Connection c, long productId, int qty) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"UPDATE stock_level SET reserved = reserved + ? WHERE product_id = ? AND on_hand - reserved >= ?")) {
			ps.setInt(1, qty);
			ps.setLong(2, productId);
			ps.setInt(3, qty);
			return ps.executeUpdate() == 1;
		}
	}

	/** Kontrprzykład: "SELECT, sprawdź w Javie, UPDATE" — tak wygląda typowy kod z encją JPA bez blokad. */
	static boolean reserveNaive(Connection c, long productId, int qty) throws SQLException {
		int onHand, reserved;
		try (PreparedStatement ps = c.prepareStatement("SELECT on_hand, reserved FROM stock_level WHERE product_id = ?")) {
			ps.setLong(1, productId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return false;
				onHand = rs.getInt(1);
				reserved = rs.getInt(2);
			}
		}
		if (onHand - reserved < qty) return false;
		try (PreparedStatement ps = c.prepareStatement("UPDATE stock_level SET reserved = ? WHERE product_id = ?")) {
			ps.setInt(1, reserved + qty); // wartość policzona w Javie, jak entity.setReserved(...) + save()
			ps.setLong(2, productId);
			ps.executeUpdate();
		}
		return true;
	}

	interface Reserve {
		boolean apply(Connection c, long productId, int qty) throws SQLException;
	}

	// ---------------------------------------------------------------- scenariusz 1

	record Outcome(int successes, int reserved) {}

	/** onHand = 5, 100 wątków, każdy rezerwuje 1 szt. we własnej transakcji. Wszyscy startują na jeden sygnał. */
	Outcome hundredBuyers(Reserve reserve) throws Exception {
		exec("TRUNCATE stock_level");
		exec("INSERT INTO stock_level (product_id, on_hand) VALUES (1, 5)");

		CountDownLatch ready = new CountDownLatch(THREADS);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				futures.add(pool.submit(() -> {
					try (Connection c = connect()) { // połączenie otwarte PRZED startem
						c.setAutoCommit(false);
						ready.countDown();
						go.await();
						if (reserve.apply(c, X, 1)) successes.incrementAndGet();
						c.commit();
					}
					return null;
				}));
			}
			ready.await();
			go.countDown();
			for (Future<?> f : futures) f.get(); // rzuci, jeśli któryś wątek dostał wyjątek (np. naruszenie CHECK)
		}
		return new Outcome(successes.get(), queryInt("SELECT reserved FROM stock_level WHERE product_id = 1"));
	}

	@Test
	void scenario1_conditionalUpdate_neverOversells() throws Exception {
		List<Outcome> outcomes = new ArrayList<>();
		for (int run = 0; run < RUNS; run++) outcomes.add(hundredBuyers(ReservationPrototypeTest::reserveConditional));
		report("1. warunkowy UPDATE (PRD 6.1)", outcomes);

		assertThat(outcomes).allSatisfy(o -> {
			assertThat(o.successes()).isEqualTo(5);
			assertThat(o.reserved()).isEqualTo(5);
		});
	}

	@Test
	void scenario1_naiveSelectThenUpdate_oversells() throws Exception {
		List<Outcome> outcomes = new ArrayList<>();
		for (int run = 0; run < RUNS; run++) outcomes.add(hundredBuyers(ReservationPrototypeTest::reserveNaive));
		report("1b. naiwny SELECT -> if -> UPDATE (kontrprzykład)", outcomes);

		// kontrprzykład ma sens tylko wtedy, gdy naprawdę psuje stan
		assertThat(outcomes).anySatisfy(o -> assertThat(o.successes()).isGreaterThan(5));
	}

	// ---------------------------------------------------------------- scenariusz 2

	enum OrderResult { OK, DEADLOCK }

	/**
	 * Zamówienie A = [X, Y], zamówienie B = [Y, X], równocześnie.
	 * Po zarezerwowaniu pierwszej pozycji każda transakcja czeka (max 500 ms), aż druga też zarezerwuje swoją
	 * pierwszą — to wymusza najgorszy przeplot zamiast liczyć na szczęście. Jeśli druga transakcja utknęła na
	 * blokadzie wiersza (wersja z sortowaniem), czekanie kończy się po 500 ms i pierwsza jedzie dalej.
	 */
	List<OrderResult> twoCrossedOrders(boolean sortByProductId) throws Exception {
		exec("TRUNCATE stock_level");
		exec("INSERT INTO stock_level (product_id, on_hand) VALUES (1, 10), (2, 10)");

		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch firstItemLocked = new CountDownLatch(2);
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			List<Future<OrderResult>> futures = new ArrayList<>();
			for (List<Long> items : List.of(List.of(X, Y), List.of(Y, X))) {
				List<Long> order = sortByProductId ? items.stream().sorted().toList() : items;
				futures.add(pool.submit(() -> {
					try (Connection c = connect()) {
						c.setAutoCommit(false);
						go.await();
						try {
							reserveConditional(c, order.get(0), 1);
							firstItemLocked.countDown();
							firstItemLocked.await(500, TimeUnit.MILLISECONDS);
							reserveConditional(c, order.get(1), 1);
							c.commit();
							return OrderResult.OK;
						} catch (SQLException e) {
							c.rollback();
							if ("40P01".equals(e.getSQLState())) { // deadlock_detected
								System.out.println("    PostgreSQL: " + e.getMessage().lines().findFirst().orElse(""));
								return OrderResult.DEADLOCK;
							}
							throw e;
						}
					}
				}));
			}
			go.countDown();
			List<OrderResult> results = new ArrayList<>();
			for (Future<OrderResult> f : futures) results.add(f.get());
			return results;
		}
	}

	@Test
	void scenario2_sortedItems_noDeadlock() throws Exception {
		int deadlocks = crossedOrdersReport("2. pozycje posortowane po ID produktu (PRD 6.1)", true);
		assertThat(deadlocks).isZero();
	}

	@Test
	void scenario2_unsortedItems_deadlock() throws Exception {
		int deadlocks = crossedOrdersReport("2b. pozycje w kolejności z żądania (kontrprzykład)", false);
		assertThat(deadlocks).isPositive();
	}

	int crossedOrdersReport(String title, boolean sorted) throws Exception {
		System.out.println("\n=== " + title + " ===");
		int deadlocks = 0, bothOk = 0;
		for (int run = 0; run < RUNS; run++) {
			List<OrderResult> r = twoCrossedOrders(sorted);
			int d = (int) r.stream().filter(x -> x == OrderResult.DEADLOCK).count();
			deadlocks += d;
			if (d == 0) bothOk++;
			int rx = queryInt("SELECT reserved FROM stock_level WHERE product_id = 1");
			int ry = queryInt("SELECT reserved FROM stock_level WHERE product_id = 2");
			System.out.printf("  run %2d: A=%s B=%s  reserved X=%d Y=%d%n", run + 1, r.get(0), r.get(1), rx, ry);
		}
		System.out.printf("  PODSUMOWANIE: %d/%d przebiegów bez deadlocka, %d deadlocków łącznie%n", bothOk, RUNS, deadlocks);
		return deadlocks;
	}

	// ---------------------------------------------------------------- pomocnicze

	static Connection connect() throws SQLException {
		return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
	}

	static void exec(String sql) throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement()) {
			s.execute(sql);
		}
	}

	static int queryInt(String sql) throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	static void report(String title, List<Outcome> outcomes) {
		System.out.println("\n=== " + title + " ===");
		for (int i = 0; i < outcomes.size(); i++) {
			Outcome o = outcomes.get(i);
			System.out.printf("  run %2d: udanych rezerwacji = %3d, reserved w bazie = %d, on_hand = 5%n",
					i + 1, o.successes(), o.reserved());
		}
		int min = outcomes.stream().mapToInt(Outcome::successes).min().orElseThrow();
		int max = outcomes.stream().mapToInt(Outcome::successes).max().orElseThrow();
		System.out.printf("  PODSUMOWANIE: udanych min=%d max=%d (powinno być zawsze 5)%n", min, max);
	}
}
