package com.cqlplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-150 — locks the CORS hardening invariants in {@link WebConfig}:
 *
 * <ul>
 *   <li>Wildcard origins are explicitly rejected (the combination of
 *       {@code allowCredentials=true} + {@code Allow-Origin: *} would be
 *       insecure even if the browser tolerated it).</li>
 *   <li>Profile-aware origin lists (dev / docker) only add the localhost
 *       entries when their respective profile is active.</li>
 * </ul>
 */
class WebConfigTest {

    private final WebConfig webConfig = new WebConfig();

    private void setProfile(String profile) {
        ReflectionTestUtils.setField(webConfig, "activeProfile", profile);
    }

    private void setExtraOrigins(String origins) {
        ReflectionTestUtils.setField(webConfig, "extraAllowedOrigins", origins);
    }

    @Test
    void corsFilter_alwaysIncludesCdsHooksSandbox() throws Exception {
        setProfile("");
        setExtraOrigins("");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns()).contains("https://sandbox.cds-hooks.org");
    }

    @Test
    void corsFilter_devProfileAddsLocalhostOrigins() throws Exception {
        setProfile("dev");
        setExtraOrigins("");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns())
                .contains("http://localhost:5173", "http://127.0.0.1:5173");
    }

    @Test
    void corsFilter_dockerProfileAddsDockerOrigins() throws Exception {
        setProfile("docker");
        setExtraOrigins("");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns())
                .contains("http://localhost:8888", "http://127.0.0.1:8888")
                .doesNotContain("http://localhost:5173"); // dev-only
    }

    @Test
    void corsFilter_prodProfileExcludesAllLocalhost() throws Exception {
        setProfile("prod");
        setExtraOrigins("");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns())
                .doesNotContain("http://localhost:5173", "http://localhost:8888");
    }

    @Test
    void corsFilter_extraOriginsAreAdded() throws Exception {
        setProfile("");
        setExtraOrigins("https://example.com,https://app.example.com");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns())
                .contains("https://example.com", "https://app.example.com");
    }

    @Test
    void corsFilter_PAT150_regression_wildcardOriginRejected() {
        setProfile("");
        setExtraOrigins("*");

        // Building the bean must throw — wildcards are unsafe with allowCredentials=true.
        assertThatThrownBy(() -> webConfig.corsFilter())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard CORS origins are not allowed");
    }

    @Test
    void corsFilter_PAT150_regression_partialWildcardRejected() {
        setProfile("");
        setExtraOrigins("https://*.example.com");

        assertThatThrownBy(() -> webConfig.corsFilter())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard CORS origins are not allowed");
    }

    @Test
    void corsFilter_emptyEntriesInExtraOriginsAreSkipped() throws Exception {
        setProfile("");
        setExtraOrigins("https://a.example.com,,  ,https://b.example.com");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowedOriginPatterns())
                .contains("https://a.example.com", "https://b.example.com");
    }

    @Test
    void corsFilter_allowCredentials_isTrue() throws Exception {
        setProfile("");
        setExtraOrigins("");

        CorsConfiguration cfg = configForFilter(webConfig.corsFilter());
        assertThat(cfg.getAllowCredentials()).isTrue();
        // Spec invariant — Allow-Origin: * with credentials is rejected by browsers.
        // Our wildcard guard means we never reach that combination.
        assertThat(cfg.getAllowedOriginPatterns()).noneMatch(o -> o.contains("*"));
    }

    /**
     * Helper to extract the CorsConfiguration bound to the {@code /**} path
     * from a {@link CorsFilter}. We have to go through reflection because
     * {@link CorsFilter} doesn't expose its source publicly.
     */
    private CorsConfiguration configForFilter(CorsFilter filter) throws Exception {
        Field sourceField = CorsFilter.class.getDeclaredField("configSource");
        sourceField.setAccessible(true);
        CorsConfigurationSource source = (CorsConfigurationSource) sourceField.get(filter);
        HttpServletRequest dummy = new MockHttpServletRequest("GET", "/api/anything");
        return source.getCorsConfiguration(dummy);
    }
}
