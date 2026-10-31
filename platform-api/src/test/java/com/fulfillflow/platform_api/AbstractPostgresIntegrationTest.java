package com.fulfillflow.platform_api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for integration tests that exercise the full application against a real
 * PostgreSQL in Testcontainers. The container's connection details are wired
 * into the Spring context by {@link ServiceConnection}; Flyway then builds the
 * schema on startup. Run only by {@code ./mvnw verify} (Failsafe), never by
 * {@code ./mvnw test}.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
}
