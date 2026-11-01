package com.fulfillflow.platform_api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests that exercise the full application against a real
 * PostgreSQL in Testcontainers. The container's connection details are wired
 * into the Spring context by {@link ServiceConnection}; Flyway then builds the
 * schema on startup. Run only by {@code ./mvnw verify} (Failsafe), never by
 * {@code ./mvnw test}.
 *
 * <p>The container is a singleton for the whole test JVM: started once here and
 * stopped by Testcontainers when the JVM exits. Do not add {@code @Testcontainers}
 * / {@code @Container}: they stop the container after each test class, while
 * Spring keeps cached contexts pointing at its old port.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	static {
		POSTGRES.start();
	}
}
