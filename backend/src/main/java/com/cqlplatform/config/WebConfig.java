package com.cqlplatform.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${cors.allowed-origins:}")
    private String extraAllowedOrigins;

    /**
     * Single source of truth for all allowed origins — used by both
     * {@link #corsFilter()} and {@link #privateNetworkFilter()}.
     */
    private List<String> getAllAllowedOrigins() {
        List<String> origins = new ArrayList<>();
        origins.add("https://sandbox.cds-hooks.org");
        if (activeProfile.contains("dev")) {
            origins.add("http://localhost:5173");
            origins.add("http://localhost:8080");
            origins.add("http://127.0.0.1:5173");
            origins.add("http://127.0.0.1:8080");
        }
        if (activeProfile.contains("docker")) {
            origins.add("http://localhost:8888");
            origins.add("http://127.0.0.1:8888");
        }
        if (extraAllowedOrigins != null && !extraAllowedOrigins.isBlank()) {
            for (String o : extraAllowedOrigins.split(",")) {
                String trimmed = o.trim();
                if (trimmed.isEmpty()) continue;
                // Reject bare wildcards and patterns — only exact origins allowed
                if ("*".equals(trimmed) || trimmed.contains("*")) {
                    throw new IllegalArgumentException(
                            "Wildcard CORS origins are not allowed. Use exact origin URLs (e.g. https://example.com). Got: " + trimmed);
                }
                origins.add(trimmed);
            }
        }
        return origins;
    }

    @Bean
    public FilterRegistrationBean<Filter> privateNetworkFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                    HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {

                // Only add the PNA header; standard CORS is handled by corsFilter()
                String origin = request.getHeader("Origin");
                if (origin != null && getAllAllowedOrigins().contains(origin)) {
                    response.setHeader("Access-Control-Allow-Private-Network", "true");
                }

                filterChain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(getAllAllowedOrigins());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
