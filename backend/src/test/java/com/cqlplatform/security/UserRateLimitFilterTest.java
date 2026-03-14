package com.cqlplatform.security;

import com.cqlplatform.config.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRateLimitFilterTest {

    private UserRateLimitFilter filter;
    private RateLimitProperties props;
    private MeterRegistry meterRegistry;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.setUserLimitEnabled(true);
        props.setUserTranslateRpm(15);
        props.setUserExecuteRpm(8);
        props.setUserFixSuggestionRpm(3);
        props.setUserDefaultRpm(40);
        meterRegistry = new SimpleMeterRegistry();
        filter = new UserRateLimitFilter(props, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipWhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipWhenAnonymousUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldApplyPerUserTranslateLimit() throws Exception {
        props.setUserTranslateRpm(3);
        filter = new UserRateLimitFilter(props, meterRegistry);

        setAuthenticatedUser("alice");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(3)).doFilter(request, response);

        // 4th should be blocked
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldIsolatePerUser() throws Exception {
        props.setUserTranslateRpm(2);
        filter = new UserRateLimitFilter(props, meterRegistry);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");

        // Exhaust alice's translate quota
        setAuthenticatedUser("alice");
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        // bob should still be allowed
        setAuthenticatedUser("bob");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(3)).doFilter(request, response);
    }

    @Test
    void shouldReturnUserRateLimitHeaders() throws Exception {
        props.setUserTranslateRpm(1);
        filter = new UserRateLimitFilter(props, meterRegistry);

        setAuthenticatedUser("carol");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-UserRateLimit-Limit", "1");
        verify(response).setHeader("X-UserRateLimit-Remaining", "0");

        // Trigger 429
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setHeader(eq("Retry-After"), anyString());
    }

    @Test
    void shouldSkipWhenDisabled() throws Exception {
        props.setUserLimitEnabled(false);
        filter = new UserRateLimitFilter(props, meterRegistry);

        setAuthenticatedUser("alice");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIncrementMetricOnRejection() throws Exception {
        props.setUserTranslateRpm(1);
        filter = new UserRateLimitFilter(props, meterRegistry);

        setAuthenticatedUser("dave");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        double count = meterRegistry.find("rate_limit_exceeded")
                .tag("tier", "TRANSLATE")
                .tag("layer", "user")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    private void setAuthenticatedUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "password", List.of()));
    }
}
