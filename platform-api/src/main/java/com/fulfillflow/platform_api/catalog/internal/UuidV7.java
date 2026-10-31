package com.fulfillflow.platform_api.catalog.internal;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates UUIDv7 identifiers (RFC 9562): a 48-bit Unix millisecond timestamp
 * followed by random bits, so the values are time-ordered and index-friendly.
 * IDs are assigned in the application before the entity is persisted (PRD 5.4).
 */
final class UuidV7 {

	private UuidV7() {
	}

	static UUID generate() {
		long timestamp = System.currentTimeMillis();
		ThreadLocalRandom random = ThreadLocalRandom.current();

		long mostSignificantBits = (timestamp << 16)  // 48-bit timestamp in the high bits
				| 0x7000L                              // version 7 (bits 12-15)
				| random.nextInt(0x1000);              // 12 random bits (rand_a)

		long leastSignificantBits = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL)  // clear the top 2 bits
				| 0x8000000000000000L;                                         // variant 10 (RFC 9562)

		return new UUID(mostSignificantBits, leastSignificantBits);
	}
}
