package com.cqlplatform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the Spring application context loads successfully.
 * When run with the ci-pg profile, this also validates:
 * - All Flyway migrations execute against PostgreSQL without errors
 * - JPA entity mappings match the migrated schema (ddl-auto: validate)
 *
 * This test is skipped unless CI_PG_TEST=true is set, to avoid
 * interfering with the normal H2-based test suite.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CI_PG_TEST", matches = "true")
class CqlPlatformApplicationTest {

    @Test
    void contextLoads() {
        // Spring context startup triggers Flyway migration + JPA schema validation.
        // If either fails, this test fails with a descriptive error.
    }
}
