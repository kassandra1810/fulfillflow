package com.fulfillflow.platform_api.prototype.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

	static final String CHECK_VIOLATION = "23514";
	static final String DEADLOCK = "40P01";
	static final String SERIALIZATION_FAILURE = "40001";

	static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17-alpine")
			.withCommand("postgres", "-c", "max_connections=250");

	@BeforeAll
	static void startDb() throws SQLException {
		PG.start();
		exec("""
				CREATE TABLE stock_level (
				    product_id BIGINT PRIMARY KEY,
				    on_hand    INT NOT NULL,
				    reserved   INT NOT NULL DEFAULT 0,
				    CHECK (reserved >= 0 AND reserved <= on_hand)
				)""");
		// tylko na potrzeby scenariusza 5: "anulowanie tylko raz" wymaga statusu zamówienia
		exec("""
				CREATE TABLE orders (
				    id         BIGINT PRIMARY KEY,
				    product_id BIGINT NOT NULL,
				    qty        INT NOT NULL,
				    status     TEXT NOT NULL
				)""");
	}

	@AfterAll
	static void stopDb() {
		PG.stop();
	}

	@BeforeEach
	void cleanTables() throws SQLException {
		exec("TRUNCATE stock_level, orders");
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

	/** PRD 6.1 + 5.3: wszystkie pozycje w jednej transakcji, posortowane po ID produktu, pierwsza bez towaru → rollback. */
	static boolean placeOrder(Connection c, Map<Long, Integer> items) throws SQLException {
		for (var item : new TreeMap<>(items).entrySet()) {
			if (!reserveConditional(c, item.getKey(), item.getValue())) {
				c.rollback();
				return false;
			}
		}
		c.commit();
		return true;
	}

	/** Reguła 5: przejście RESERVED → CANCELLED to warunkowy UPDATE; zwalniamy towar tylko, jeśli to my zmieniliśmy status. */
	static boolean cancelGuarded(Connection c, long orderId) throws SQLException {
		long productId;
		int qty;
		try (PreparedStatement ps = c.prepareStatement(
				"UPDATE orders SET status = 'CANCELLED' WHERE id = ? AND status = 'RESERVED' RETURNING product_id, qty")) {
			ps.setLong(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return false; // już anulowane — nic nie zwalniamy, bez błędu
				productId = rs.getLong(1);
				qty = rs.getInt(2);
			}
		}
		release(c, productId, qty);
		return true;
	}

	/** Kontrprzykład: status sprawdzony SELECT-em w Javie, potem zwolnienie i zmiana statusu. */
	static boolean cancelNaive(Connection c, long orderId) throws SQLException {
		long productId;
		int qty;
		try (PreparedStatement ps = c.prepareStatement("SELECT product_id, qty, status FROM orders WHERE id = ?")) {
			ps.setLong(1, orderId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				if (!"RESERVED".equals(rs.getString(3))) return false;
				productId = rs.getLong(1);
				qty = rs.getInt(2);
			}
		}
		release(c, productId, qty);
		exec(c, "UPDATE orders SET status = 'CANCELLED' WHERE id = " + orderId);
		return true;
	}

	static void release(Connection c, long productId, int qty) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("UPDATE stock_level SET reserved = reserved - ? WHERE product_id = ?")) {
			ps.setInt(1, qty);
			ps.setLong(2, productId);
			ps.executeUpdate();
		}
	}

	interface Reserve {
		boolean apply(Connection c, long productId, int qty) throws SQLException;
	}

	// ---------------------------------------------------------------- wyścig

	interface Task {
		boolean run(Connection c) throws Exception;
	}

	/** ok = zadanie zwróciło true; sqlState != null = zadanie dostało wyjątek z bazy (i zostało wycofane). */
	record Result(boolean ok, String sqlState) {
		boolean failedWith(String state) { return state.equals(sqlState); }
	}

	/**
	 * Każde zadanie dostaje własne połączenie (autoCommit = false, podany poziom izolacji), otwarte PRZED startem.
	 * Wszystkie startują na jeden sygnał. Zadanie samo robi commit; wyjątek SQL → rollback i zapis SQLState.
	 */
	static List<Result> race(int isolation, List<Task> tasks) throws Exception {
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
			List<Future<Result>> futures = new ArrayList<>();
			for (Task task : tasks) {
				futures.add(pool.submit(() -> {
					try (Connection c = connect()) {
						c.setAutoCommit(false);
						c.setTransactionIsolation(isolation);
						ready.countDown();
						go.await();
						try {
							return new Result(task.run(c), null);
						} catch (SQLException e) {
							c.rollback();
							return new Result(false, e.getSQLState());
						}
					}
				}));
			}
			ready.await();
			go.countDown();
			List<Result> results = new ArrayList<>();
			for (Future<Result> f : futures) results.add(f.get());
			return results;
		}
	}

	static List<Task> times(int n, Task task) {
		return new ArrayList<>(java.util.Collections.nCopies(n, task));
	}

	// ---------------------------------------------------------------- scenariusz 1: 100 kupujących, 5 sztuk

	record Outcome(int successes, int rejected, int serializationFailures, int otherErrors, int reserved) {}

	Outcome hundredBuyers(int isolation, Reserve reserve) throws Exception {
		exec("TRUNCATE stock_level");
		exec("INSERT INTO stock_level (product_id, on_hand) VALUES (1, 5)");
		List<Result> results = race(isolation, times(THREADS, c -> {
			boolean ok = reserve.apply(c, X, 1);
			c.commit();
			return ok;
		}));
		return new Outcome(
				count(results, r -> r.ok()),
				count(results, r -> !r.ok() && r.sqlState() == null),
				count(results, r -> r.failedWith(SERIALIZATION_FAILURE)),
				count(results, r -> r.sqlState() != null && !r.failedWith(SERIALIZATION_FAILURE)),
				reservedOf(X));
	}

	List<Outcome> repeatHundredBuyers(String title, int isolation, Reserve reserve) throws Exception {
		List<Outcome> outcomes = new ArrayList<>();
		for (int run = 0; run < RUNS; run++) outcomes.add(hundredBuyers(isolation, reserve));
		System.out.println("\n=== " + title + " ===");
		for (int i = 0; i < outcomes.size(); i++) {
			Outcome o = outcomes.get(i);
			System.out.printf("  run %2d: udane=%3d  odrzucone(0 wierszy)=%3d  błąd serializacji=%3d  inne błędy=%d  reserved=%d/5%n",
					i + 1, o.successes(), o.rejected(), o.serializationFailures(), o.otherErrors(), o.reserved());
		}
		System.out.printf("  PODSUMOWANIE: udanych min=%d max=%d%n",
				outcomes.stream().mapToInt(Outcome::successes).min().orElseThrow(),
				outcomes.stream().mapToInt(Outcome::successes).max().orElseThrow());
		return outcomes;
	}

	@Test
	void scenario1_conditionalUpdate_neverOversells() throws Exception {
		var outcomes = repeatHundredBuyers("1. warunkowy UPDATE (PRD 6.1), READ COMMITTED",
				Connection.TRANSACTION_READ_COMMITTED, ReservationPrototypeTest::reserveConditional);
		assertThat(outcomes).allSatisfy(o -> {
			assertThat(o.successes()).isEqualTo(5);
			assertThat(o.rejected()).isEqualTo(95);
			assertThat(o.reserved()).isEqualTo(5);
		});
	}

	@Test
	void scenario1_naiveSelectThenUpdate_oversells() throws Exception {
		var outcomes = repeatHundredBuyers("1b. naiwny SELECT -> if -> UPDATE (kontrprzykład)",
				Connection.TRANSACTION_READ_COMMITTED, ReservationPrototypeTest::reserveNaive);
		// kontrprzykład ma sens tylko wtedy, gdy naprawdę psuje stan
		assertThat(outcomes).anySatisfy(o -> assertThat(o.successes()).isGreaterThan(5));
	}

	@Test
	void isolation_repeatableRead_noOversellButSpuriousFailures() throws Exception {
		var outcomes = repeatHundredBuyers("7a. warunkowy UPDATE przy REPEATABLE READ",
				Connection.TRANSACTION_REPEATABLE_READ, ReservationPrototypeTest::reserveConditional);
		assertThat(outcomes).allSatisfy(o -> {
			assertThat(o.successes()).isLessThanOrEqualTo(5);
			assertThat(o.reserved()).isEqualTo(o.successes());
		});
		assertThat(outcomes).anySatisfy(o -> assertThat(o.serializationFailures()).isPositive());
	}

	@Test
	void isolation_serializable_noOversellButSpuriousFailures() throws Exception {
		var outcomes = repeatHundredBuyers("7b. warunkowy UPDATE przy SERIALIZABLE",
				Connection.TRANSACTION_SERIALIZABLE, ReservationPrototypeTest::reserveConditional);
		assertThat(outcomes).allSatisfy(o -> {
			assertThat(o.successes()).isLessThanOrEqualTo(5);
			assertThat(o.reserved()).isEqualTo(o.successes());
		});
		assertThat(outcomes).anySatisfy(o -> assertThat(o.serializationFailures()).isPositive());
	}

	// ---------------------------------------------------------------- scenariusz 2: zamówienia [X,Y] i [Y,X]

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

		CountDownLatch firstItemLocked = new CountDownLatch(2);
		List<Task> orders = new ArrayList<>();
		for (List<Long> items : List.of(List.of(X, Y), List.of(Y, X))) {
			List<Long> order = sortByProductId ? items.stream().sorted().toList() : items;
			orders.add(c -> {
				reserveConditional(c, order.get(0), 1);
				firstItemLocked.countDown();
				firstItemLocked.await(500, TimeUnit.MILLISECONDS);
				reserveConditional(c, order.get(1), 1);
				c.commit();
				return true;
			});
		}
		List<Result> results = race(Connection.TRANSACTION_READ_COMMITTED, orders);
		for (Result r : results) {
			if (r.sqlState() != null && !r.failedWith(DEADLOCK)) throw new AssertionError("nieoczekiwany błąd " + r);
		}
		return results.stream().map(r -> r.ok() ? OrderResult.OK : OrderResult.DEADLOCK).toList();
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
			System.out.printf("  run %2d: A=%s B=%s  reserved X=%d Y=%d%n", run + 1, r.get(0), r.get(1), reservedOf(X), reservedOf(Y));
		}
		System.out.printf("  PODSUMOWANIE: %d/%d przebiegów bez deadlocka, %d deadlocków łącznie%n", bothOk, RUNS, deadlocks);
		return deadlocks;
	}

	// ---------------------------------------------------------------- scenariusz 4: wszystko albo nic

	@Test
	void scenario4_secondItemOutOfStock_firstItemRolledBack() throws Exception {
		exec("INSERT INTO stock_level (product_id, on_hand, reserved) VALUES (1, 5, 1), (2, 3, 3)");
		boolean placed;
		try (Connection c = connect()) {
			c.setAutoCommit(false);
			placed = placeOrder(c, Map.of(X, 2, Y, 1)); // X dostępne (4), Y brak (0)
		}
		System.out.printf("%n=== 4. wszystko albo nic: [X:2 dostępne, Y:1 brak] ===%n  przyjęte=%s  reserved X=%d (przed: 1)  Y=%d (przed: 3)%n",
				placed, reservedOf(X), reservedOf(Y));
		assertThat(placed).isFalse();
		assertThat(reservedOf(X)).isEqualTo(1);
		assertThat(reservedOf(Y)).isEqualTo(3);
	}

	@Test
	void scenario4_missingStockRow_treatedAsOutOfStock() throws Exception {
		exec("INSERT INTO stock_level (product_id, on_hand) VALUES (1, 5)"); // Y nigdy nieprzyjęty: brak wiersza
		boolean placed;
		try (Connection c = connect()) {
			c.setAutoCommit(false);
			placed = placeOrder(c, Map.of(X, 2, Y, 1)); // nie rzuca wyjątku — po prostu 0 wierszy
		}
		System.out.printf("%n=== 4b. brak wiersza stock_level dla Y ===%n  przyjęte=%s  reserved X=%d%n", placed, reservedOf(X));
		assertThat(placed).isFalse();
		assertThat(reservedOf(X)).isZero();
	}

	// ---------------------------------------------------------------- scenariusz 5: anulowanie równolegle z rezerwacjami

	record CancelOutcome(int releases, int buyers, int reserved, int checkViolations) {
		/** Zamówienie anulowane → w reserved powinni zostać tylko nowi kupujący. */
		boolean consistent() { return releases == 1 && reserved == buyers; }
	}

	/**
	 * Stan: on_hand = 5, zamówienie #100 trzyma 2 szt. (reserved = 2).
	 * Równocześnie: 10 prób anulowania #100 (np. klient klika kilka razy) + 50 nowych kupujących po 1 szt.
	 */
	CancelOutcome cancelDuringReservations(Task cancel) throws Exception {
		exec("TRUNCATE stock_level, orders");
		exec("INSERT INTO stock_level (product_id, on_hand, reserved) VALUES (1, 5, 2)");
		exec("INSERT INTO orders VALUES (100, 1, 2, 'RESERVED')");

		List<Task> tasks = times(10, cancel);
		List<Task> buyers = times(50, c -> {
			boolean ok = reserveConditional(c, X, 1);
			c.commit();
			return ok;
		});
		tasks.addAll(buyers);
		List<Result> results = race(Connection.TRANSACTION_READ_COMMITTED, tasks);
		List<Result> cancels = results.subList(0, 10), buys = results.subList(10, results.size());
		return new CancelOutcome(
				count(cancels, Result::ok),
				count(buys, Result::ok),
				reservedOf(X),
				count(results, r -> r.failedWith(CHECK_VIOLATION)));
	}

	List<CancelOutcome> repeatCancel(String title, Task cancel) throws Exception {
		System.out.println("\n=== " + title + " ===");
		List<CancelOutcome> outcomes = new ArrayList<>();
		for (int run = 0; run < RUNS; run++) {
			CancelOutcome o = cancelDuringReservations(cancel);
			outcomes.add(o);
			System.out.printf("  run %2d: zwolnień=%d  nowych rezerwacji=%d  reserved=%d  odrzuconych przez CHECK=%d  %s%n",
					run + 1, o.releases(), o.buyers(), o.reserved(), o.checkViolations(), o.consistent() ? "OK" : "NIESPÓJNE");
		}
		return outcomes;
	}

	@Test
	void scenario5_guardedCancel_releasesExactlyOnce() throws Exception {
		var outcomes = repeatCancel("5. anulowanie z warunkiem na statusie (WHERE status = 'RESERVED')",
				c -> {
					boolean released = cancelGuarded(c, 100);
					c.commit();
					return released;
				});
		assertThat(outcomes).allSatisfy(o -> {
			assertThat(o.consistent()).isTrue();
			assertThat(o.buyers()).isLessThanOrEqualTo(5);
		});
	}

	@Test
	void scenario5_naiveCancel_releasesMoreThanOnce() throws Exception {
		var outcomes = repeatCancel("5b. anulowanie: SELECT status -> if -> zwolnij (kontrprzykład)",
				c -> {
					boolean released = cancelNaive(c, 100);
					c.commit();
					return released;
				});
		assertThat(outcomes).anySatisfy(o -> assertThat(o.consistent()).isFalse());
	}

	// ---------------------------------------------------------------- scenariusz 6: CHECK jako druga linia obrony

	@Test
	void scenario6_checkConstraintRejectsInvalidWrites() throws Exception {
		exec("INSERT INTO stock_level (product_id, on_hand, reserved) VALUES (1, 5, 3)");
		System.out.println("\n=== 6. CHECK (reserved >= 0 AND reserved <= on_hand) ===");
		for (String sql : List.of(
				"UPDATE stock_level SET reserved = 6 WHERE product_id = 1",   // reserved > on_hand
				"UPDATE stock_level SET reserved = -1 WHERE product_id = 1",  // reserved < 0
				"UPDATE stock_level SET on_hand = 2 WHERE product_id = 1")) { // korekta on_hand poniżej reserved
			assertThatThrownBy(() -> exec(sql))
					.isInstanceOfSatisfying(SQLException.class, e -> {
						System.out.printf("  %-58s -> %s %s%n", sql, e.getSQLState(), e.getMessage().lines().findFirst().orElse(""));
						assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION);
					});
		}
		assertThat(reservedOf(X)).isEqualTo(3);
	}

	// ---------------------------------------------------------------- pomocnicze

	static Connection connect() throws SQLException {
		return DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
	}

	static void exec(String sql) throws SQLException {
		try (Connection c = connect()) {
			exec(c, sql);
		}
	}

	static void exec(Connection c, String sql) throws SQLException {
		try (Statement s = c.createStatement()) {
			s.execute(sql);
		}
	}

	static int reservedOf(long productId) throws SQLException {
		try (Connection c = connect(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery("SELECT reserved FROM stock_level WHERE product_id = " + productId)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	static int count(List<Result> results, java.util.function.Predicate<Result> p) {
		return (int) results.stream().filter(p).count();
	}
}
