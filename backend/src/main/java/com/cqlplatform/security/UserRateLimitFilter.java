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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class UserRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final MeterRegistry meterRegistry;
    private final Map<String, RateLimitFilter.TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private static final long CLEANUP_INTERVAL_MS = 300_000;

    public UserRateLimitFilter(RateLimitProperties props, MeterRegistry meterRegistry) {
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!props.isUserLimitEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Periodic cleanup
        long now = System.currentTimeMillis();
        if (now - lastCleanup.get() > CLEANUP_INTERVAL_MS) {
            if (lastCleanup.compareAndSet(lastCleanup.get(), now)) {
                buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(CLEANUP_INTERVAL_MS));
            }
        }

        String username = auth.getName();
        RateLimitFilter.RateTier tier = RateLimitFilter.resolveTier(request);
        int rpmForTier = getUserRpmForTier(tier);
        String bucketKey = username + ":" + tier.name();

        RateLimitFilter.TokenBucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> new RateLimitFilter.TokenBucket(rpmForTier));

        if (bucket.tryConsume(1)) {
            response.setHeader("X-UserRateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-UserRateLimit-Remaining", String.valueOf(bucket.getRemaining()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, bucket.getSecondsUntilRefill());
            response.setHeader("X-UserRateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-UserRateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            Counter.builder("rate_limit_exceeded")
                    .tag("tier", tier.name())
                    .tag("layer", "user")
                    .register(meterRegistry)
                    .increment();

            log.warn("User rate limit exceeded for user: {} tier: {}", username, tier);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"User rate limit exceeded. Try again later.\"}");
        }
    }

    private int getUserRpmForTier(RateLimitFilter.RateTier tier) {
        return switch (tier) {
            case TRANSLATE -> props.getUserTranslateRpm();
            case EXECUTE -> props.getUserExecuteRpm();
            case FIX_SUGGESTION -> props.getUserFixSuggestionRpm();
            default -> props.getUserDefaultRpm();
        };
    }
}
