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

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantRateLimitFilterTest {

    private TenantRateLimitFilter filter;
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
        props.setTenantLimitEnabled(true);
        props.setTenantTranslateRpm(60);
        props.setTenantExecuteRpm(32);
        props.setTenantFixSuggestionRpm(12);
        props.setTenantDefaultRpm(160);
        meterRegistry = new SimpleMeterRegistry();
        filter = new TenantRateLimitFilter(props, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSkipWhenNoTenant() throws Exception {
        // No tenant on the request → the IP + per-user layers own it.
        TenantContext.clear();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldApplyPerTenantTranslateLimit() throws Exception {
        props.setTenantTranslateRpm(3);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        TenantContext.setCurrentTenantId(1L);
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
    void shouldAggregateAcrossUsersOfSameTenant() throws Exception {
        // The whole point of the tenant layer: two different users of the same clinic
        // share ONE bucket, so their combined traffic hits the ceiling.
        props.setTenantTranslateRpm(2);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Same tenant, regardless of which user — the filter keys on tenant only.
        TenantContext.setCurrentTenantId(1L);
        filter.doFilterInternal(request, response, filterChain); // user A req 1
        filter.doFilterInternal(request, response, filterChain); // user B req 1 (same tenant)

        // Third request from the tenant (any user) is over the aggregate ceiling.
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldIsolatePerTenant() throws Exception {
        props.setTenantTranslateRpm(2);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");

        // Exhaust tenant 1's translate quota
        TenantContext.setCurrentTenantId(1L);
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        // tenant 2 should still be allowed
        TenantContext.setCurrentTenantId(2L);
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(3)).doFilter(request, response);
    }

    @Test
    void shouldReturnTenantRateLimitHeaders() throws Exception {
        props.setTenantTranslateRpm(1);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        TenantContext.setCurrentTenantId(7L);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-TenantRateLimit-Limit", "1");
        verify(response).setHeader("X-TenantRateLimit-Remaining", "0");

        // Trigger 429
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setHeader(eq("Retry-After"), anyString());
    }

    @Test
    void shouldSkipWhenDisabled() throws Exception {
        props.setTenantLimitEnabled(false);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        TenantContext.setCurrentTenantId(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIncrementMetricOnRejection() throws Exception {
        props.setTenantTranslateRpm(1);
        filter = new TenantRateLimitFilter(props, meterRegistry);

        TenantContext.setCurrentTenantId(9L);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        double count = meterRegistry.find("rate_limit_exceeded")
                .tag("tier", "TRANSLATE")
                .tag("layer", "tenant")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }
}
