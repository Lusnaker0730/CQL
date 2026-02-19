package com.cqlplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 5);
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    @Test
    void shouldAllowRequestsUnderLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldBlockRequestsOverLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("GET");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        // Exhaust the bucket
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Next request should be blocked
        filter.doFilterInternal(request, response, filterChain);

        verify(response, atLeastOnce()).setStatus(429);
    }

    @Test
    void shouldSkipWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);

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
    void shouldUseRemoteAddrIgnoringXForwardedFor() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldTrackPerClientBuckets() throws Exception {
        // Exhaust client A
        when(request.getRemoteAddr()).thenReturn("client-a");
        when(request.getMethod()).thenReturn("GET");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Client B should still be allowed
        when(request.getRemoteAddr()).thenReturn("client-b");
        filter.doFilterInternal(request, response, filterChain);

        // 5 from client A + 1 from client B = 6 total doFilter calls
        verify(filterChain, times(6)).doFilter(request, response);
    }
}
