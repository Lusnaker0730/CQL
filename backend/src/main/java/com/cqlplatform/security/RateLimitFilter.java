package com.cqlplatform.security;

import com.cqlplatform.config.RateLimitProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final MeterRegistry meterRegistry;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes

    public RateLimitFilter(RateLimitProperties props, MeterRegistry meterRegistry) {
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    public enum RateTier {
        TRANSLATE, EXECUTE, FIX_SUGGESTION, LIBRARY_READ, AUTH, CDS_INVOKE, CDS_DISCOVERY, DEFAULT
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!props.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Periodic cleanup of expired buckets
        long now = System.currentTimeMillis();
        if (now - lastCleanup.get() > CLEANUP_INTERVAL_MS) {
            if (lastCleanup.compareAndSet(lastCleanup.get(), now)) {
                buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(CLEANUP_INTERVAL_MS));
            }
        }

        RateTier tier = resolveTier(request);
        int rpmForTier = getRpmForTier(tier);
        String clientIp = getClientKey(request);
        String bucketKey = clientIp + ":" + tier.name();

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(rpmForTier));

        int cost = computeTokenCost(request, tier);

        if (bucket.tryConsume(cost)) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, bucket.getSecondsUntilRefill());
            response.setHeader("X-RateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            Counter.builder("rate_limit_exceeded")
                    .tag("tier", tier.name())
                    .tag("layer", "ip")
                    .register(meterRegistry)
                    .increment();

            log.warn("Rate limit exceeded for client: {} tier: {} (cost={})", clientIp, tier, cost);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    static RateTier resolveTier(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path != null) {
            // Sensitive credential endpoints share the tight AUTH tier. reset-password
            // (token exchange that sets a new password) belongs here alongside
            // login/register/forgot-password — it was previously falling into the 6x
            // looser DEFAULT tier. refresh/logout stay in DEFAULT on purpose: legit
            // clients hit refresh frequently, and AUTH's low RPM would break them.
            if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
                    || path.startsWith("/api/auth/forgot-password")
                    || path.startsWith("/api/auth/reset-password")
                    || path.startsWith("/api/auth/clinic-applications")) {
                return RateTier.AUTH;
            }
            if (path.startsWith("/cds-services/") && "POST".equalsIgnoreCase(method)) {
                return RateTier.CDS_INVOKE;
            }
            // Discovery endpoint is unauthenticated per CDS Hooks spec. Lower rate
            // limit than DEFAULT tier so drive-by enumeration — attacker listing
            // the full set of enabled services to plan targeted attacks — becomes
            // expensive without breaking legitimate EHR bootstrap traffic.
            // Matches both /cds-services and /cds-services/ (with or without slash).
            if (("/cds-services".equals(path) || "/cds-services/".equals(path))
                    && "GET".equalsIgnoreCase(method)) {
                return RateTier.CDS_DISCOVERY;
            }
        }
        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/cql/translate".equals(path)) return RateTier.TRANSLATE;
            if ("/api/cql/execute".equals(path)) return RateTier.EXECUTE;
            if ("/api/cql/fix-suggestion".equals(path)) return RateTier.FIX_SUGGESTION;
        }
        if ("GET".equalsIgnoreCase(method) && path != null && path.startsWith("/api/cql/libraries")) {
            return RateTier.LIBRARY_READ;
        }
        return RateTier.DEFAULT;
    }

    int getRpmForTier(RateTier tier) {
        return switch (tier) {
            case TRANSLATE -> props.getTranslateRpm();
            case EXECUTE -> props.getExecuteRpm();
            case FIX_SUGGESTION -> props.getFixSuggestionRpm();
            case LIBRARY_READ -> props.getLibraryReadRpm();
            case AUTH -> props.getAuthRpm();
            case CDS_INVOKE -> props.getCdsInvokeRpm();
            case CDS_DISCOVERY -> props.getCdsDiscoveryRpm();
            case DEFAULT -> props.getRequestsPerMinute();
        };
    }

    int computeTokenCost(HttpServletRequest request, RateTier tier) {
        if (tier != RateTier.TRANSLATE) return 1;
        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0) return 1;
        if (contentLength > props.getPayloadTier4()) return 5;
        if (contentLength > props.getPayloadTier3()) return 3;
        if (contentLength > props.getPayloadTier2()) return 2;
        return 1;
    }

    private String getClientKey(HttpServletRequest request) {
        // Resolve the REAL client IP, not the proxy container's address. Keying on
        // request.getRemoteAddr() here collapsed every external client (behind the
        // Cloudflare → nginx chain) into one shared bucket — a single attacker could
        // exhaust the whole site's AUTH bucket and lock all users out. Shared with
        // AuditFilter so the two filters agree on client identity.
        return ClientIpResolver.resolve(request);
    }

    static class TokenBucket {
        private final int maxTokens;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        private volatile long lastAccessTime;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicLong(maxTokens);
            this.lastRefillTime = System.currentTimeMillis();
            this.lastAccessTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(int cost) {
            refill();
            lastAccessTime = System.currentTimeMillis();
            long current = tokens.get();
            if (current >= cost) {
                tokens.addAndGet(-cost);
                return true;
            }
            return false;
        }

        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - lastAccessTime > expirationMs;
        }

        int getRemaining() {
            return (int) Math.max(0, tokens.get());
        }

        synchronized long getSecondsUntilRefill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            long tokensToAdd = (elapsed * maxTokens) / 60_000;
            if (tokensToAdd > 0) {
                // Some tokens would be added now, so next refill is soon
                return 1;
            }
            // Time until next token is added
            long msPerToken = 60_000 / Math.max(1, maxTokens);
            long remaining = msPerToken - elapsed;
            return Math.max(1, remaining / 1000);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed >= 60_000) {
                tokens.set(maxTokens);
                lastRefillTime = now;
            } else {
                long tokensToAdd = (elapsed * maxTokens) / 60_000;
                if (tokensToAdd > 0) {
                    long newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                    tokens.set(newTokens);
                    // Advance lastRefillTime by the time consumed, not to 'now'
                    lastRefillTime += (tokensToAdd * 60_000) / maxTokens;
                }
            }
        }
    }
}
