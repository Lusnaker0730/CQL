package com.cqlplatform.security;

import com.cqlplatform.config.RateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
class RateLimitFilterTest {

    private RateLimitFilter filter;
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
        props.setEnabled(true);
        props.setRequestsPerMinute(60);
        props.setTranslateRpm(20);
        props.setExecuteRpm(10);
        props.setFixSuggestionRpm(5);
        props.setLibraryReadRpm(120);
        props.setAuthRpm(10);
        props.setCdsInvokeRpm(10);
        props.setPayloadTier2(10_240);
        props.setPayloadTier3(51_200);
        props.setPayloadTier4(204_800);
        meterRegistry = new SimpleMeterRegistry();
        filter = new RateLimitFilter(props, meterRegistry);
    }

    @Test
    void shouldApplyTranslateTierLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // 20 requests should succeed
        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(20)).doFilter(request, response);

        // 21st should be blocked
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldApplyExecuteTierLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/execute");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(10)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldApplyDefaultTierForOtherPaths() throws Exception {
        props.setRequestsPerMinute(5);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.3");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/artifacts");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(5)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldApplyLibraryReadTierLimit() throws Exception {
        props.setLibraryReadRpm(3);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/cql/libraries");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(3)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldConsumeMultipleTokensForLargePayload() throws Exception {
        props.setTranslateRpm(10);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        // 300 KB payload -> cost 5
        when(request.getContentLengthLong()).thenReturn(300_000L);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Each request costs 5 tokens, so 2 should succeed (10 tokens / 5 cost = 2)
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(2)).doFilter(request, response);

        // 3rd should be blocked (only 0 tokens remain)
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldIsolateBucketsPerTier() throws Exception {
        props.setTranslateRpm(2);
        props.setLibraryReadRpm(100);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.6");

        // Exhaust translate tier
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        // Library reads should still work
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/cql/libraries");
        filter.doFilterInternal(request, response, filterChain);

        // 2 translate + 1 library = 3
        verify(filterChain, times(3)).doFilter(request, response);
    }

    @Test
    void shouldReturnRetryAfterHeader() throws Exception {
        props.setTranslateRpm(1);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.7");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Exhaust
        filter.doFilterInternal(request, response, filterChain);

        // Should be blocked with Retry-After
        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setHeader(eq("Retry-After"), anyString());
    }

    @Test
    void shouldSkipWhenDisabled() throws Exception {
        props.setEnabled(false);
        filter = new RateLimitFilter(props, meterRegistry);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipOptionsRequests() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldTrackPerClientBuckets() throws Exception {
        props.setRequestsPerMinute(2);
        filter = new RateLimitFilter(props, meterRegistry);

        // Exhaust client A
        when(request.getRemoteAddr()).thenReturn("client-a");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/other");
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        // Client B should still be allowed
        when(request.getRemoteAddr()).thenReturn("client-b");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(3)).doFilter(request, response);
    }

    @Test
    void shouldResolveTierCorrectly() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.TRANSLATE);

        when(request.getRequestURI()).thenReturn("/api/cql/execute");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.EXECUTE);

        when(request.getRequestURI()).thenReturn("/api/cql/fix-suggestion");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.FIX_SUGGESTION);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/cql/libraries");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.LIBRARY_READ);

        when(request.getRequestURI()).thenReturn("/api/artifacts");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.DEFAULT);

        // AUTH tier
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.AUTH);

        when(request.getRequestURI()).thenReturn("/api/auth/register");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.AUTH);

        when(request.getRequestURI()).thenReturn("/api/auth/forgot-password");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.AUTH);

        // CDS_INVOKE tier — POST only
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/cds-services/my-service");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.CDS_INVOKE);

        // CDS discovery (GET /cds-services) routes to its own CDS_DISCOVERY tier
        // (tighter than DEFAULT to curb unauth enumeration). Supports both forms.
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/cds-services/");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.CDS_DISCOVERY);

        when(request.getRequestURI()).thenReturn("/cds-services");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.CDS_DISCOVERY);

        // But GET on a service instance (per-user discovery suffix) stays DEFAULT —
        // it doesn't enumerate the global catalog so it doesn't need the tight tier.
        when(request.getRequestURI()).thenReturn("/cds-services/u/alice");
        assertThat(RateLimitFilter.resolveTier(request)).isEqualTo(RateLimitFilter.RateTier.DEFAULT);
    }

    @Test
    void shouldApplyAuthTierLimit() throws Exception {
        props.setAuthRpm(3);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.1.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(3)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldApplyCdsInvokeTierLimit() throws Exception {
        props.setCdsInvokeRpm(2);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.1.2");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/cds-services/my-hook");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 2; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(2)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldApplyCdsDiscoveryTierLimit() throws Exception {
        // CDS_DISCOVERY tier — GET /cds-services. Tighter than DEFAULT (60 RPM)
        // because the endpoint is unauth'd per CDS spec and ripe for enumeration.
        props.setCdsDiscoveryRpm(2);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.1.3");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/cds-services");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        for (int i = 0; i < 2; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(filterChain, times(2)).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void cdsDiscoveryAndInvokeTiersShouldHaveIndependentBuckets() throws Exception {
        // Discovery GETs and invocation POSTs from the same IP must not share a
        // bucket — an attacker saturating discovery shouldn't block legitimate
        // invocations and vice versa.
        props.setCdsDiscoveryRpm(1);
        props.setCdsInvokeRpm(1);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.1.4");
        // No response writer stub — neither of the two calls below exceed its bucket.

        // Burn discovery bucket
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/cds-services");
        filter.doFilterInternal(request, response, filterChain);

        // Invocation bucket should still have capacity
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/cds-services/my-hook");
        filter.doFilterInternal(request, response, filterChain);

        // Both legitimate calls made it through
        verify(filterChain, times(2)).doFilter(request, response);
    }

    @Test
    void shouldComputeTokenCostByPayloadSize() {
        when(request.getContentLengthLong()).thenReturn(500L);
        assertThat(filter.computeTokenCost(request, RateLimitFilter.RateTier.TRANSLATE)).isEqualTo(1);

        when(request.getContentLengthLong()).thenReturn(15_000L);
        assertThat(filter.computeTokenCost(request, RateLimitFilter.RateTier.TRANSLATE)).isEqualTo(2);

        when(request.getContentLengthLong()).thenReturn(60_000L);
        assertThat(filter.computeTokenCost(request, RateLimitFilter.RateTier.TRANSLATE)).isEqualTo(3);

        when(request.getContentLengthLong()).thenReturn(300_000L);
        assertThat(filter.computeTokenCost(request, RateLimitFilter.RateTier.TRANSLATE)).isEqualTo(5);

        // Non-TRANSLATE tier always returns 1
        assertThat(filter.computeTokenCost(request, RateLimitFilter.RateTier.EXECUTE)).isEqualTo(1);
    }

    @Test
    void shouldIncrementMetricOnRejection() throws Exception {
        props.setTranslateRpm(1);
        filter = new RateLimitFilter(props, meterRegistry);

        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/cql/translate");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        double count = meterRegistry.find("rate_limit_exceeded")
                .tag("tier", "TRANSLATE")
                .tag("layer", "ip")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }
}
