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

import com.cqlplatform.util.StringUtils;

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

    // EHR patient operations: /api/ehr/connections/{connId}/patients/{patientId}/...
    private static final Pattern EHR_PATIENT_PATTERN =
            Pattern.compile("/api/ehr/connections/(\\d+)/patients/([^/]+)");

    // EHR connection operations: /api/ehr/connections/{connId}
    private static final Pattern EHR_CONNECTION_PATTERN =
            Pattern.compile("/api/ehr/connections/(\\d+)");

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
                    queryParameters = StringUtils.truncate(request.getQueryString(), 2000);
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
                queryParameters = StringUtils.truncate(request.getQueryString(), 2000);
            }

            // Bulk Data Export — highest-risk PHI operation
            if (path.contains("/fhir/$export")) {
                phiAccess = true;
                resourceType = "BulkExport";
                resourceId = null;
                action = "EXPORT";
                queryParameters = StringUtils.truncate(request.getQueryString(), 2000);
            }

            // EHR connection and patient audit fields
            Long connectionId = null;
            String patientFhirId = null;
            String connectionName = null;

            Matcher ehrPatientMatcher = EHR_PATIENT_PATTERN.matcher(path);
            if (ehrPatientMatcher.find()) {
                connectionId = Long.valueOf(ehrPatientMatcher.group(1));
                patientFhirId = ehrPatientMatcher.group(2);
                phiAccess = true;
                queryParameters = StringUtils.truncate(request.getQueryString(), 2000);
                connectionName = (String) request.getAttribute("ehr.connectionName");
            } else {
                Matcher ehrConnMatcher = EHR_CONNECTION_PATTERN.matcher(path);
                if (ehrConnMatcher.find()) {
                    connectionId = Long.valueOf(ehrConnMatcher.group(1));
                    connectionName = (String) request.getAttribute("ehr.connectionName");
                }
            }

            // Batch import — mark as PHI access
            if (path.contains("/batch-import")) {
                phiAccess = true;
                action = "BATCH_IMPORT";
            }

            String requestId = (String) request.getAttribute(RequestTracingFilter.MDC_REQUEST_ID);

            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .username(username)
                    .method(request.getMethod())
                    .path(StringUtils.truncate(path, 500))
                    .resourceType(resourceType)
                    .resourceId(StringUtils.truncate(resourceId, 100))
                    .action(action)
                    .statusCode(response.getStatus())
                    .ipAddress(StringUtils.truncate(getClientIp(request), 45))
                    .userAgent(StringUtils.truncate(request.getHeader("User-Agent"), 500))
                    .responseTimeMs(duration)
                    .phiAccess(phiAccess)
                    .queryParameters(queryParameters)
                    .requestId(requestId)
                    .connectionId(connectionId)
                    .patientFhirId(StringUtils.truncate(patientFhirId, 200))
                    .connectionName(StringUtils.truncate(connectionName, 200))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // PAT-143: was log.warn — but PHI access / EXPORT / BATCH_IMPORT audit
            // failures are compliance-relevant evidence loss. WARN is filtered out by
            // most SIEM configs; ERROR with stack trace ensures the alert reaches ops.
            // Note: request itself was already processed (filterChain.doFilter is
            // before this catch), so this is post-action logging — auth was still
            // enforced by upstream filters; we just lost the audit record.
            log.error("Failed to write audit log for {} {}: {}",
                    request.getMethod(), path, e.getMessage(), e);
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

    /**
     * Resolve the real client IP address. Delegates to {@link ClientIpResolver}
     * (shared with {@link RateLimitFilter}) so audit records and rate-limit buckets
     * always agree on client identity. See ClientIpResolver for the XFF trust model.
     */
    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

}
