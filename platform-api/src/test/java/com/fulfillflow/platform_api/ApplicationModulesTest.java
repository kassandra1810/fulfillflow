package com.fulfillflow.platform_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Guards the modular-monolith boundaries (PRD 5.5, spec seam 2): a broken module
 * boundary or a dependency cycle fails this test and so the build. Pure static
 * analysis (ArchUnit) over the compiled classes — no Spring context, no database
 * — so it runs in {@code ./mvnw test} (Surefire), not only in {@code verify}.
 */
class ApplicationModulesTest {

	private final ApplicationModules modules = ApplicationModules.of(PlatformApiApplication.class);

	@Test
	void detectsTheExpectedModules() {
		// Without this, verify() would pass vacuously if a package move left it
		// detecting no modules at all.
		assertThat(modules.getModuleByName("catalog")).isPresent();
		assertThat(modules.getModuleByName("web")).isPresent();
	}

	@Test
	void verifiesModuleBoundaries() {
		modules.verify();
	}
}
