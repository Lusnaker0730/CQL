package com.cqlplatform.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {


    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "https://sandbox.cds-hooks.org",
            "http://localhost:5173",
            "http://localhost:8080",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:8080");

    @Bean
    public FilterRegistrationBean<Filter> privateNetworkFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                    HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {

                String origin = request.getHeader("Origin");

                if ("OPTIONS".equalsIgnoreCase(request.getMethod()) && origin != null) {
                    // Handle CORS + PNA preflight completely
                    if (isAllowedOrigin(origin)) {
                        response.setHeader("Access-Control-Allow-Origin", origin);
                        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH");
                        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin");
                        response.setHeader("Access-Control-Allow-Credentials", "true");
                        response.setHeader("Access-Control-Allow-Private-Network", "true");
                        response.setHeader("Access-Control-Max-Age", "3600");
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        return;
                    }
                }

                // For non-preflight requests, still add PNA and CORS headers
                if (origin != null && isAllowedOrigin(origin)) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Access-Control-Allow-Credentials", "true");
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
        config.setAllowedOriginPatterns(Arrays.asList(
                "https://sandbox.cds-hooks.org",
                "http://localhost:*",
                "http://127.0.0.1:*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    private static boolean isAllowedOrigin(String origin) {
        return ALLOWED_ORIGINS.contains(origin);
    }
}
