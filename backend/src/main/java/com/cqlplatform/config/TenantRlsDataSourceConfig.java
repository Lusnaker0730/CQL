package com.cqlplatform.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * PAT-223 — wraps the auto-configured {@code dataSource} bean in {@link TenantAwareDataSource}
 * when it points at PostgreSQL, so every connection checkout carries the caller's tenant
 * into the session variables the V70 Row-Level Security policies read.
 *
 * <p>A {@link BeanPostProcessor} (declared {@code static} so it is instantiated before the
 * beans it post-processes) keeps Spring Boot's Hikari auto-configuration, metrics unwrapping
 * and health indicator untouched. H2 (test profile) is left as-is: the policies only exist
 * on PostgreSQL.
 */
@Configuration
@Slf4j
public class TenantRlsDataSourceConfig {

    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!"dataSource".equals(beanName) || !(bean instanceof DataSource)
                        || bean instanceof TenantAwareDataSource) {
                    return bean;
                }
                String url = bean instanceof HikariDataSource hikari ? hikari.getJdbcUrl() : null;
                if (url == null || !url.startsWith("jdbc:postgresql:")) {
                    log.info("Tenant RLS scoping not installed on dataSource (url={}): not PostgreSQL", url);
                    return bean;
                }
                log.info("Tenant RLS scoping installed on dataSource ({})", url);
                return new TenantAwareDataSource((DataSource) bean);
            }
        };
    }
}
