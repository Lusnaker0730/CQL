package com.cqlplatform.config;

import com.cqlplatform.security.AuditFilter;
import com.cqlplatform.security.JwtAuthenticationFilter;
import com.cqlplatform.security.RateLimitFilter;
import com.cqlplatform.security.RequestTracingFilter;
import com.cqlplatform.security.UserRateLimitFilter;
import com.cqlplatform.security.XssFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RequestTracingFilter requestTracingFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuditFilter auditFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UserRateLimitFilter userRateLimitFilter;
    private final XssFilter xssFilter;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${management.prometheus.public:false}")
    private boolean prometheusPublic;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String frameAncestors = h2ConsoleEnabled ? "'self'" : "'none'";

        http
                // CSRF is intentionally disabled (H10 rationale):
                // This is a stateless JWT API — every mutating request must carry a
                // short-lived Bearer token in the Authorization header, which a
                // cross-site form or script cannot access due to the Same-Origin Policy.
                // The refresh-token cookie is protected against CSRF by:
                //   • HttpOnly=true   (JavaScript cannot read it)
                //   • SameSite=Strict (browser will not send it on cross-site requests)
                //   • Secure=true     (HTTPS only in production, controlled by refresh-token.cookie-secure)
                //   • Path=/api/auth  (scoped to the auth endpoint only)
                // Therefore a traditional CSRF synchronizer token would provide no
                // additional protection and would complicate API clients unnecessarily.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.frameOptions(frame -> {
                                if (h2ConsoleEnabled) { frame.sameOrigin(); } else { frame.deny(); }
                            })
                            .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                            .contentTypeOptions(content -> {})
                            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                            + "img-src 'self' data:; font-src 'self'; frame-ancestors " + frameAncestors));
                })
                .authorizeHttpRequests(auth -> configureAuthorization(auth))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Filter order: Tracing → RateLimit → XSS → JWT → UserRateLimit → Audit
                .addFilterBefore(requestTracingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, RequestTracingFilter.class)
                .addFilterBefore(xssFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userRateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(auditFilter, UserRateLimitFilter.class);

        return http.build();
    }

    private void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // Public auth endpoints
        auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/okta/config").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/okta/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                // SMART on FHIR configuration
                .requestMatchers("/.well-known/smart-configuration").permitAll()
                // FHIR Subscription callback (external FHIR servers can't authenticate)
                .requestMatchers(HttpMethod.POST, "/api/ehr/subscriptions/callback").permitAll()
                // CDS Hooks sandbox requires authentication
                .requestMatchers(HttpMethod.POST, "/cds-services/*/sandbox").authenticated()
                // Per-user CDS endpoints (API key auth handled in filter)
                .requestMatchers("/cds-services/u/**").permitAll()
                // CDS Hooks discovery and invocation (external EHR systems)
                .requestMatchers("/cds-services/**").permitAll()
                // User API key management
                .requestMatchers("/api/user/api-keys/**").authenticated()
                // Actuator: health is always public
                .requestMatchers("/actuator/health").permitAll()
                // Deploy-change detection for SPA cache-bust — no sensitive data exposed
                .requestMatchers(HttpMethod.GET, "/api/version").permitAll();

        // Prometheus: public only when management.prometheus.public=true (docker/internal network)
        if (prometheusPublic) {
            auth.requestMatchers("/actuator/prometheus").permitAll();
        } else {
            auth.requestMatchers("/actuator/prometheus").authenticated();
        }

        // Swagger/OpenAPI: public in dev, authenticated in production
        if (h2ConsoleEnabled) {
            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                    .requestMatchers("/h2-console/**").permitAll();
        } else {
            auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").authenticated();
        }

        // ADMIN only endpoints
        auth.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "DEPARTMENT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/fhir/cache/**").hasRole("ADMIN")
                // CDS service deletion: ownership enforced in controller (allows user to delete own)
                .requestMatchers(HttpMethod.DELETE, "/api/cds/services/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/measures/**").hasRole("ADMIN")
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
