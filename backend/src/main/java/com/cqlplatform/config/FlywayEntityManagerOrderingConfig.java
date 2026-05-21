package com.cqlplatform.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In Spring Boot 4 the JPA auto-configuration was repackaged
 * ({@code org.springframework.boot.hibernate.autoconfigure.HibernateJpaConfiguration})
 * and no longer auto-registers the dependency that makes
 * {@code entityManagerFactory} wait for Flyway. With {@code ddl-auto: validate}
 * Hibernate then runs schema validation before Flyway migrates, producing
 * "Schema validation: missing table [...]" on a fresh database.
 *
 * <p>Restore the 3.x behavior explicitly by depending on the well-known
 * Flyway bean names {@code flyway} + {@code flywayInitializer}. Conditional
 * on the class so the bean only registers when Flyway is on the classpath.
 */
@Configuration
@ConditionalOnBean(Flyway.class)
public class FlywayEntityManagerOrderingConfig {

    @Bean
    static EntityManagerFactoryDependsOnFlyway entityManagerFactoryDependsOnFlyway() {
        return new EntityManagerFactoryDependsOnFlyway();
    }

    static class EntityManagerFactoryDependsOnFlyway extends EntityManagerFactoryDependsOnPostProcessor {
        EntityManagerFactoryDependsOnFlyway() {
            super("flyway", "flywayInitializer");
        }
    }
}
