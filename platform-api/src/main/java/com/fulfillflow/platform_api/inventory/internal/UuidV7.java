package com.fulfillflow.platform_api.inventory.internal;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUIDv7 identifiers (RFC 9562): a 48-bit Unix millisecond timestamp
 * followed by random bits, so the values are time-ordered and index-friendly.
 * IDs are assigned in the application before the entity is persisted (PRD 5.4).
 *
 * <p>This mirrors the catalog module's generator. The two cannot share one copy
 * without one module reaching into the other's {@code internal} package, which
 * the module boundary (and Spring Modulith's {@code verify()}) forbids; a tiny,
 * dependency-free duplicate is the lesser evil over coupling the modules.
 */
final class UuidV7 {

	// IDs must be unpredictable (PRD 5.4, RFC 9562 §6.9), so the random bits come
	// from a CSPRNG; SecureRandom is thread-safe.
	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	static UUID generate() {
		long timestamp = System.currentTimeMillis();

		long mostSignificantBits = (timestamp << 16)  // 48-bit timestamp in the high bits
				| 0x7000L                              // version 7 (bits 12-15)
				| RANDOM.nextInt(0x1000);              // 12 random bits (rand_a)

		long leastSignificantBits = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL)  // clear the top 2 bits
				| 0x8000000000000000L;                                         // variant 10 (RFC 9562)

		return new UUID(mostSignificantBits, leastSignificantBits);
	}
}
