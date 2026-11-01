package com.fulfillflow.platform_api;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Guards the modular-monolith boundaries (PRD 5.5, spec seam 2): a broken module
 * boundary or a dependency cycle fails this test and so the build. Pure static
 * analysis (ArchUnit) over the compiled classes — no Spring context, no database
 * — so it runs in {@code ./mvnw test} (Surefire), not only in {@code verify}.
 */
class ApplicationModulesTest {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(PlatformApiApplication.class).verify();
	}
}
