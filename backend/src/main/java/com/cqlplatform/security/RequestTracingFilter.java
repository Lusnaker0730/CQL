package com.cqlplatform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates SLF4J MDC with request-scoped tracing context.
 *
 * <p>Must be the FIRST filter in the chain so that every downstream log
 * statement — including security, audit, and exception handling — carries
 * a consistent {@code requestId}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Read {@code X-Request-ID} header from the incoming request (front-end generated).</li>
 *   <li>If absent, generate a new UUID.</li>
 *   <li>Put {@code requestId}, {@code requestUri}, {@code requestMethod} into MDC.</li>
 *   <li>Echo the ID back via the {@code X-Request-ID} response header.</li>
 *   <li>After the filter chain completes, populate {@code userId} (available only
 *       after authentication) and then clear MDC.</li>
 * </ol>
 */
@Component
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_REQUEST_URI = "requestUri";
    public static final String MDC_REQUEST_METHOD = "requestMethod";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // Populate MDC before any downstream logging
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_TRACE_ID, requestId);  // alias for logback config compatibility
        MDC.put(MDC_REQUEST_URI, request.getRequestURI());
        MDC.put(MDC_REQUEST_METHOD, request.getMethod());

        // Echo back so the front-end can correlate
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // Store in request attribute so AuditFilter/GlobalExceptionHandler can read it
        request.setAttribute(MDC_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Populate userId after authentication has completed
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                MDC.put(MDC_USER_ID, auth.getName());
            }
            // Clear all MDC keys to prevent leaking into thread-pool reuse
            MDC.clear();
        }
    }
}
