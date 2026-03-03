package com.cqlplatform.security;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.repository.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditFilter extends OncePerRequestFilter {

    private final AuditLogRepository auditLogRepository;

    private static final Set<String> AUDITED_PREFIXES = Set.of("/api/");

    // /api/fhir/{resourceType}/{id}  or  /api/fhir/{resourceType}
    private static final Pattern FHIR_RESOURCE_PATTERN =
            Pattern.compile("/api/fhir/(\\w+)(?:/([^/]+))?");

    // /api/{module}/{id}  (non-fhir)
    private static final Pattern RESOURCE_PATTERN =
            Pattern.compile("/api/(\\w+)(?:/([^/]+))?");

    /** FHIR resource types whose read/search constitutes PHI access. */
    private static final Set<String> PHI_RESOURCE_TYPES = Set.of(
            "Patient", "Observation", "Condition", "Procedure", "Encounter",
            "MedicationRequest", "MedicationStatement", "AllergyIntolerance",
            "Immunization", "DiagnosticReport", "DocumentReference",
            "CarePlan", "CareTeam", "Goal", "ServiceRequest",
            "Coverage", "Claim", "ExplanationOfBenefit");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!shouldAudit(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        long duration = System.currentTimeMillis() - startTime;

        try {
            String username = getUsername();
            String action = mapAction(request.getMethod());
            String resourceType = null;
            String resourceId = null;
            boolean phiAccess = false;
            String queryParameters = null;

            // Try FHIR 3-level path first: /api/fhir/{resourceType}/{id}
            Matcher fhirMatcher = FHIR_RESOURCE_PATTERN.matcher(path);
            if (fhirMatcher.find()) {
                resourceType = fhirMatcher.group(1);
                resourceId = fhirMatcher.group(2);

                if (PHI_RESOURCE_TYPES.contains(resourceType)) {
                    phiAccess = true;
                    queryParameters = truncate(request.getQueryString(), 2000);
                }
            } else {
                // Fallback: generic /api/{module}/{id}
                Matcher matcher = RESOURCE_PATTERN.matcher(path);
                if (matcher.find()) {
                    resourceType = matcher.group(1);
                    resourceId = matcher.group(2);
                }
            }

            // Also mark demographic search as PHI access
            if (path.contains("Patient/$search-by-demographics")) {
                phiAccess = true;
                resourceType = "Patient";
                queryParameters = truncate(request.getQueryString(), 2000);
            }

            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .username(username)
                    .method(request.getMethod())
                    .path(path)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .action(action)
                    .statusCode(response.getStatus())
                    .ipAddress(getClientIp(request))
                    .userAgent(truncate(request.getHeader("User-Agent"), 500))
                    .responseTimeMs(duration)
                    .phiAccess(phiAccess)
                    .queryParameters(queryParameters)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log for {} {}: {}", request.getMethod(), path, e.getMessage());
        }
    }

    private boolean shouldAudit(String path) {
        return AUDITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String mapAction(String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> "READ";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
