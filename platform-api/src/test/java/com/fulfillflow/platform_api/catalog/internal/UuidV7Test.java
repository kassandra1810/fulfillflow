package com.fulfillflow.platform_api.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

	@Test
	void hasVersion7AndRfc9562Variant() {
		UUID id = UuidV7.generate();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void embedsCurrentUnixMillisInTheTop48Bits() {
		long before = System.currentTimeMillis();
		UUID id = UuidV7.generate();
		long after = System.currentTimeMillis();

		long embedded = id.getMostSignificantBits() >>> 16;
		assertThat(embedded).isBetween(before, after);
	}

	@Test
	void idsFromLaterMillisecondsSortAfterEarlierOnes() throws InterruptedException {
		UUID earlier = UuidV7.generate();
		Thread.sleep(2);
		UUID later = UuidV7.generate();

		assertThat(later).isGreaterThan(earlier);
	}

	@Test
	void generatesNoDuplicates() {
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 100_000; i++) {
			ids.add(UuidV7.generate());
		}

		assertThat(ids).hasSize(100_000);
	}
}
