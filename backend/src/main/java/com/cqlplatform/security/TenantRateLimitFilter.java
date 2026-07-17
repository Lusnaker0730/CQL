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

/**
 * Per-tenant aggregate rate limiting (PAT-213).
 *
 * <p>Now that clinics self-provision as isolated tenants, the IP and per-user layers alone
 * leave a gap: a single clinic with many staff (or many API keys) can, in aggregate,
 * saturate a shared platform tier and starve other tenants. This filter caps the combined
 * traffic of ALL of a tenant's users on each rate tier.
 *
 * <p>It keys buckets on the caller's tenant id — populated by {@link JwtAuthenticationFilter}
 * from the JWT tenant claim (or the API-key owner's tenant) into {@link TenantContext} — so it
 * MUST run after that filter. Requests with no tenant (anonymous discovery, legacy platform
 * accounts whose token carries no tenant claim) are left to the IP + per-user layers; a
 * per-tenant ceiling has no meaning without a tenant.
 *
 * <p>Mirrors {@link UserRateLimitFilter}; reuses {@link RateLimitFilter}'s TokenBucket /
 * RateTier / resolveTier so tier semantics stay identical across all three layers.
 */
@Component
@Slf4j
public class TenantRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final MeterRegistry meterRegistry;
    private final Map<String, RateLimitFilter.TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private static final long CLEANUP_INTERVAL_MS = 300_000;

    public TenantRateLimitFilter(RateLimitProperties props, MeterRegistry meterRegistry) {
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!props.isTenantLimitEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // No tenant on this request → the IP + per-user layers own it. A per-tenant ceiling
        // is only meaningful for provisioned clinics that carry a tenant claim.
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
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

        RateLimitFilter.RateTier tier = RateLimitFilter.resolveTier(request);
        int rpmForTier = getTenantRpmForTier(tier);
        String bucketKey = tenantId + ":" + tier.name();

        RateLimitFilter.TokenBucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> new RateLimitFilter.TokenBucket(rpmForTier));

        if (bucket.tryConsume(1)) {
            response.setHeader("X-TenantRateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-TenantRateLimit-Remaining", String.valueOf(bucket.getRemaining()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, bucket.getSecondsUntilRefill());
            response.setHeader("X-TenantRateLimit-Limit", String.valueOf(rpmForTier));
            response.setHeader("X-TenantRateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            Counter.builder("rate_limit_exceeded")
                    .tag("tier", tier.name())
                    .tag("layer", "tenant")
                    .register(meterRegistry)
                    .increment();

            log.warn("Tenant rate limit exceeded for tenant: {} tier: {}", tenantId, tier);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Tenant rate limit exceeded. Try again later.\"}");
        }
    }

    private int getTenantRpmForTier(RateLimitFilter.RateTier tier) {
        return switch (tier) {
            case TRANSLATE -> props.getTenantTranslateRpm();
            case EXECUTE -> props.getTenantExecuteRpm();
            case FIX_SUGGESTION -> props.getTenantFixSuggestionRpm();
            default -> props.getTenantDefaultRpm();
        };
    }
}
