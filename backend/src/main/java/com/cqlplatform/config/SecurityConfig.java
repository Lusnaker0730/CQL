package com.cqlplatform.config;

import com.cqlplatform.security.AuditFilter;
import com.cqlplatform.security.JwtAuthenticationFilter;
import com.cqlplatform.security.RateLimitFilter;
import com.cqlplatform.security.RequestTracingFilter;
import com.cqlplatform.security.UserRateLimitFilter;
import com.cqlplatform.security.XssFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
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

    @Value("${management.prometheus.auth.username:}")
    private String prometheusAuthUsername;

    @Value("${management.prometheus.auth.password:}")
    private String prometheusAuthPassword;

    /**
     * PAT-150 — make the Prometheus scrape auth state visible at startup so ops
     * can confirm whether scrapes will succeed. The chain is intentionally
     * fail-closed (returns 401 for everything when credentials are blank), so
     * a misconfiguration silently breaks scraping until someone notices the
     * Prometheus dashboard going stale; this log line shortcuts that.
     */
    @PostConstruct
    void logPrometheusAuthState() {
        if (prometheusPublic) {
            log.warn("Prometheus scrape endpoint is PUBLIC (management.prometheus.public=true) — "
                    + "do not run this in production unless the network already restricts access");
            return;
        }
        boolean credsConfigured = prometheusAuthUsername != null && !prometheusAuthUsername.isBlank()
                && prometheusAuthPassword != null && !prometheusAuthPassword.isBlank();
        if (credsConfigured) {
            log.info("Prometheus scrape auth: ENABLED (basic auth, username={})", prometheusAuthUsername);
        } else {
            log.warn("Prometheus scrape auth: BROKEN — management.prometheus.public=false but "
                    + "MANAGEMENT_PROMETHEUS_AUTH_USERNAME / MANAGEMENT_PROMETHEUS_AUTH_PASSWORD are not set; "
                    + "all scrape requests will return 401 until configured");
        }
    }

    /**
     * PAT-113: dedicated filter chain for {@code /actuator/prometheus} with HTTP
     * Basic auth, so Prometheus can scrape without a JWT. Only active when
     * {@code management.prometheus.public=false} AND both auth.username /
     * auth.password are set. Higher {@code @Order} so it preempts the main
     * JWT-based chain for this path.
     *
     * <p>Registered unconditionally — when credentials are blank the chain
     * rejects all requests, forcing the ops team to configure auth rather than
     * silently falling back to anonymous access.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain prometheusSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/prometheus")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (prometheusPublic) {
                        auth.anyRequest().permitAll();
                    } else {
                        auth.anyRequest().hasRole("METRICS");
                    }
                })
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(prometheusUserDetailsService());
        return http.build();
    }

    private UserDetailsService prometheusUserDetailsService() {
        if (prometheusAuthUsername.isBlank() || prometheusAuthPassword.isBlank()) {
            // No credentials configured → no basic-auth user exists.
            // With hasRole("METRICS") that means every request is 401/403;
            // ops sees the broken scrape, checks config, fixes env vars.
            return username -> { throw new UsernameNotFoundException("metrics auth not configured"); };
        }
        UserDetails metrics = User.builder()
                .username(prometheusAuthUsername)
                .password(passwordEncoder().encode(prometheusAuthPassword))
                .roles("METRICS")
                .build();
        return new InMemoryUserDetailsManager(metrics);
    }

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
                .requestMatchers(HttpMethod.POST, "/api/auth/clinic-applications").permitAll()
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

        // /actuator/prometheus is handled by the dedicated @Order(0) chain
        // (prometheusSecurityChain) — this chain never sees that path.

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
