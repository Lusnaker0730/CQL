package com.cqlplatform.service.cds;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.model.cds.CdsServiceDefinition;
import com.cqlplatform.service.fhir.FhirClientFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves CDS Hooks prefetch templates by substituting context variables
 * (e.g. {{context.patientId}}) and fetching resources from the client's FHIR server.
 *
 * Per the CDS Hooks spec, when prefetch data is not provided by the client,
 * the service should resolve templates against the client's FHIR server
 * using the client's authorization token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrefetchResolver {

    private final FhirClientFactory fhirClientFactory;

    /**
     * Resolve prefetch templates by substituting context variables and fetching
     * resources from the FHIR server.
     *
     * @param templates Map of prefetch key to template (with query field containing e.g. "Patient/{{context.patientId}}")
     * @param request   The CDS request containing context and FHIR server info
     * @return Map of prefetch key to resolved FHIR Resource
     */
    public Map<String, Resource> resolve(
            Map<String, CdsServiceDefinition.PrefetchTemplate> templates,
            CdsRequest request) {
        return resolveWithStatus(templates, request).getResources();
    }

    /**
     * Same as {@link #resolve} but also returns per-key status for debug output.
     */
    public ResolutionResult resolveWithStatus(
            Map<String, CdsServiceDefinition.PrefetchTemplate> templates,
            CdsRequest request) {

        Map<String, Resource> resolved = new HashMap<>();
        List<CdsResponse.PrefetchStatus> statuses = new ArrayList<>();
        String fhirServer = request.getFhirServer();

        if (fhirServer == null || fhirServer.isBlank()) {
            log.warn("No fhirServer URL in request, cannot resolve prefetch templates");
            return new ResolutionResult(resolved, statuses);
        }

        String patientId = request.getContext() != null ? request.getContext().getPatientId() : null;
        String encounterId = request.getContext() != null ? request.getContext().getEncounterId() : null;
        String userId = request.getContext() != null ? request.getContext().getUserId() : null;

        IGenericClient client = fhirClientFactory.createClient(fhirServer);
        if (request.getFhirAuthorization() != null && !request.getFhirAuthorization().isBlank()) {
            client.registerInterceptor(new BearerTokenAuthInterceptor(request.getFhirAuthorization()));
        }

        for (Map.Entry<String, CdsServiceDefinition.PrefetchTemplate> entry : templates.entrySet()) {
            String key = entry.getKey();
            String queryTemplate = entry.getValue().getQuery();

            if (queryTemplate == null || queryTemplate.isBlank()) {
                log.debug("Skipping empty prefetch template for key '{}'", key);
                statuses.add(CdsResponse.PrefetchStatus.builder()
                        .key(key).status("empty").count(0).build());
                continue;
            }

            String resolvedQuery = substituteTemplate(queryTemplate, patientId, encounterId, userId);
            String resolvedUrl = fhirServer + "/" + resolvedQuery;
            long start = System.currentTimeMillis();

            try {
                log.info("Resolving prefetch template '{}': {} -> {}", key, queryTemplate, resolvedQuery);
                Resource resource = client.read()
                        .resource(Resource.class)
                        .withUrl(resolvedUrl)
                        .execute();

                long elapsed = System.currentTimeMillis() - start;
                int count = countResources(resource);

                if (resource != null) {
                    resolved.put(key, resource);
                    log.info("Resolved prefetch '{}': {} ({} resources)", key, resource.fhirType(), count);
                }
                statuses.add(CdsResponse.PrefetchStatus.builder()
                        .key(key).status("success").count(count)
                        .elapsedMs(elapsed).resolvedUrl(resolvedUrl).build());
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("Failed to resolve prefetch template '{}': {}", key, e.getMessage());
                statuses.add(CdsResponse.PrefetchStatus.builder()
                        .key(key).status("failed").count(0)
                        .elapsedMs(elapsed).resolvedUrl(resolvedUrl)
                        .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .build());
                // Partial resolution is OK per CDS Hooks spec — skip failed keys
            }
        }

        log.info("Resolved {}/{} prefetch templates", resolved.size(), templates.size());
        return new ResolutionResult(resolved, statuses);
    }

    /** Counts resources inside a Bundle, or 1 for a single resource. */
    private static int countResources(Resource resource) {
        if (resource == null) return 0;
        if (resource instanceof Bundle bundle) {
            return bundle.hasEntry() ? bundle.getEntry().size() : 0;
        }
        return 1;
    }

    @Getter
    public static class ResolutionResult {
        private final Map<String, Resource> resources;
        private final List<CdsResponse.PrefetchStatus> statuses;

        public ResolutionResult(Map<String, Resource> resources, List<CdsResponse.PrefetchStatus> statuses) {
            this.resources = resources;
            this.statuses = statuses;
        }
    }

    /**
     * Substitute CDS Hooks template variables in the query string.
     * Supports: {{context.patientId}}, {{context.encounterId}}, {{context.userId}}
     */
    private String substituteTemplate(String template, String patientId, String encounterId, String userId) {
        String result = template;
        if (patientId != null) {
            result = result.replace("{{context.patientId}}", patientId);
        }
        if (encounterId != null) {
            result = result.replace("{{context.encounterId}}", encounterId);
        }
        if (userId != null) {
            result = result.replace("{{context.userId}}", userId);
        }
        return result;
    }
}
