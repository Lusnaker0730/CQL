package com.cqlplatform.config;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

/**
 * Spring Boot 4 removed the implicit `springSecurity()` configurer that
 * `SpringBootMockMvcBuilderCustomizer` previously applied when both
 * `spring-security-test` and `@AutoConfigureMockMvc` were on the classpath
 * (controller tests then started getting 401 from {@code HttpStatusEntryPoint}
 * instead of having {@code @WithMockUser} take effect).
 *
 * <p>This config exposes a {@link MockMvcBuilderCustomizer} that re-applies
 * the security configurer globally for tests, restoring 3.x behavior without
 * having to touch every {@code @SpringBootTest + @AutoConfigureMockMvc} class.
 *
 * <p>Lives in {@code src/test/java} so it never leaks into production classpath.
 */
@Configuration
public class TestMockMvcSecurityConfig {

    @Bean
    MockMvcBuilderCustomizer applySpringSecurityToMockMvc() {
        return new MockMvcBuilderCustomizer() {
            @Override
            public void customize(ConfigurableMockMvcBuilder<?> builder) {
                builder.apply(SecurityMockMvcConfigurers.springSecurity());
            }
        };
    }
}
