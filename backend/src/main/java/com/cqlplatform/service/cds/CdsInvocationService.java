package com.cqlplatform.service.cds;

import com.cqlplatform.exception.CdsInvocationException;
import com.cqlplatform.exception.CdsInvocationException.Phase;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.service.cql.CqlExecutionService;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates CDS service invocation: prefetch handling, CQL execution,
 * and card generation via the appropriate strategy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CdsInvocationService {

    private final CqlExecutionService executionService;
    private final CqlTupleCardStrategy tupleStrategy;
    private final PlanDefinitionCardStrategy planDefinitionStrategy;
    private final FhirContext fhirContext;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer cdsInvocationTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cdsInvocationCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cdsInvocationErrorCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsAnalyticsService analyticsService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PrefetchResolver prefetchResolver;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsRecentInvocationsService recentInvocationsService;

    /**
     * Invoke a CDS service: execute CQL and generate cards.
     */
    public CdsResponse invoke(CdsHooksService.CdsServiceConfig config, CdsRequest request) {
        String patientId = request.getContext() != null ? request.getContext().getPatientId() : "unknown";
        boolean dryRun = request.isDryRun();
        boolean debugMode = request.isDebugMode() || dryRun; // dry-run implies debug
        log.info("Invoking CDS service: {} for patient: {} (debugMode={}, dryRun={})",
                config.getId(), patientId, debugMode, dryRun);
        if (cdsInvocationCounter != null)
            cdsInvocationCounter.increment();
        Timer.Sample sample = cdsInvocationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        InvocationDiagnostics diagnostics = new InvocationDiagnostics();

        try {
            CqlExecutionRequest execRequest = new CqlExecutionRequest();
            execRequest.setCql(config.getCqlContent());
            execRequest.setPatientId(request.getContext() != null ? request.getContext().getPatientId() : null);
            execRequest.setDebugMode(debugMode);

            RetrieveProvider prefetchProvider = buildPrefetchProviderSafe(request, diagnostics);

            // If no prefetch data, try dynamic resolution from prefetch templates
            if (prefetchProvider == null && prefetchResolver != null
                    && config.getPrefetch() != null && !config.getPrefetch().isEmpty()
                    && request.getFhirServer() != null && !request.getFhirServer().isBlank()) {
                prefetchProvider = resolvePrefetchTemplates(config, request, diagnostics);
            }

            // Diagnose FHIR server usage (when prefetch didn't cover everything)
            if (prefetchProvider == null) {
                String fhirServer = request.getFhirServer();
                if (fhirServer != null && (fhirServer.contains("/r2/") || fhirServer.contains("/dstu2/"))) {
                    log.warn("Client FHIR server is DSTU2 ({}), falling back to default R4 server", fhirServer);
                    execRequest.setFhirServerUrl(null);
                    diagnostics.setFhirServerDiagnostics(CdsResponse.FhirServerDiagnostics.builder()
                            .url(fhirServer).status("not_attempted")
                            .errorCategory("unsupported_version")
                            .errorMessage("Client FHIR server is DSTU2; fell back to default R4 server")
                            .build());
                } else {
                    execRequest.setFhirServerUrl(fhirServer);
                    if (fhirServer != null) {
                        diagnostics.setFhirServerDiagnostics(CdsResponse.FhirServerDiagnostics.builder()
                                .url(fhirServer).status("attempted").build());
                    }
                }
            }

            // Feature 4: context diagnostics (warnings for missing patientId, binding issues)
            diagnostics.getContextWarnings().addAll(collectContextWarnings(request, prefetchProvider));

            // Feature 8: dry-run short-circuit — skip CQL execution and return diagnostics only
            if (dryRun) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (sample != null && cdsInvocationTimer != null) sample.stop(cdsInvocationTimer);
                if (recentInvocationsService != null) {
                    recentInvocationsService.record(CdsRecentInvocationsService.InvocationRecord.builder()
                            .timestamp(java.time.Instant.now())
                            .serviceId(config.getId()).hook(config.getHook())
                            .patientId(patientId).success(true).elapsedMs(elapsed)
                            .errorPhase("dry_run")
                            .build());
                }
                Map<String, Integer> resourceCounts = (prefetchProvider instanceof PrefetchRetrieveProvider prp)
                        ? prp.getResourceCountsByType() : Map.of();
                return CdsResponse.builder()
                        .cards(List.of())
                        .debug(toDebugInfo(config, request, diagnostics)
                                .dryRun(true)
                                .resourcesByType(resourceCounts)
                                .build())
                        .build();
            }

            CqlExecutionResponse execResponse;
            try {
                execResponse = executeCql(execRequest, prefetchProvider);
                if (diagnostics.getFhirServerDiagnostics() != null
                        && "attempted".equals(diagnostics.getFhirServerDiagnostics().getStatus())) {
                    diagnostics.getFhirServerDiagnostics().setStatus("success");
                }
            } catch (CdsInvocationException ex) {
                // Categorize FHIR server error if applicable
                if (diagnostics.getFhirServerDiagnostics() != null
                        && "attempted".equals(diagnostics.getFhirServerDiagnostics().getStatus())) {
                    categorizeFhirServerError(diagnostics.getFhirServerDiagnostics(),
                            ex.getCause() != null ? ex.getCause() : ex);
                }
                throw ex;
            }

            CdsResponse response = buildCards(config, execResponse);

            if (sample != null && cdsInvocationTimer != null)
                sample.stop(cdsInvocationTimer);

            long elapsed = System.currentTimeMillis() - startTime;
            if (analyticsService != null) {
                analyticsService.recordInvocation(config.getId(), elapsed, true);
            }
            if (recentInvocationsService != null) {
                recentInvocationsService.record(CdsRecentInvocationsService.InvocationRecord.builder()
                        .timestamp(java.time.Instant.now())
                        .serviceId(config.getId()).hook(config.getHook())
                        .patientId(patientId).success(true).elapsedMs(elapsed)
                        .build());
            }

            if (debugMode) {
                response.setDebug(toDebugInfo(config, request, diagnostics)
                        .debugTrace(execResponse.getDebugTrace())
                        .build());
            }

            return response;
        } catch (Exception e) {
            if (cdsInvocationErrorCounter != null)
                cdsInvocationErrorCounter.increment();
            if (sample != null && cdsInvocationTimer != null)
                sample.stop(cdsInvocationTimer);

            long elapsed = System.currentTimeMillis() - startTime;
            if (analyticsService != null) {
                analyticsService.recordInvocation(config.getId(), elapsed, false);
            }

            log.error("CDS service invocation failed", e);

            Phase phase = (e instanceof CdsInvocationException cie) ? cie.getPhase() : Phase.UNKNOWN;
            Throwable root = (e instanceof CdsInvocationException && e.getCause() != null) ? e.getCause() : e;

            if (recentInvocationsService != null) {
                recentInvocationsService.record(CdsRecentInvocationsService.InvocationRecord.builder()
                        .timestamp(java.time.Instant.now())
                        .serviceId(config.getId()).hook(config.getHook())
                        .patientId(patientId).success(false).elapsedMs(elapsed)
                        .errorPhase(phase.name().toLowerCase())
                        .errorMessage(root.getMessage())
                        .build());
            }

            CdsResponse.CdsResponseBuilder errorResponse = CdsResponse.builder()
                    .cards(List.of(tupleStrategy.createErrorCard(root.getMessage())));

            if (debugMode) {
                errorResponse.debug(toDebugInfo(config, request, diagnostics)
                        .error(buildErrorInfo(phase, root))
                        .build());
            }

            return errorResponse.build();
        }
    }

    /** Builder for CdsDebugInfo pre-filled with invocation context + diagnostics accumulated so far. */
    private CdsResponse.CdsDebugInfo.CdsDebugInfoBuilder toDebugInfo(
            CdsHooksService.CdsServiceConfig config, CdsRequest request, InvocationDiagnostics d) {
        return CdsResponse.CdsDebugInfo.builder()
                .invocationContext(buildInvocationContext(config, request))
                .prefetchStatus(d.getPrefetchStatus().isEmpty() ? null : d.getPrefetchStatus())
                .contextWarnings(d.getContextWarnings().isEmpty() ? null : d.getContextWarnings())
                .fhirServerDiagnostics(d.getFhirServerDiagnostics());
    }

    private RetrieveProvider buildPrefetchProviderSafe(CdsRequest request, InvocationDiagnostics diagnostics) {
        try {
            return buildPrefetchProvider(request, diagnostics);
        } catch (Exception e) {
            throw new CdsInvocationException(Phase.PREFETCH_PROVIDER_BUILD, e);
        }
    }

    private RetrieveProvider resolvePrefetchTemplates(
            CdsHooksService.CdsServiceConfig config, CdsRequest request, InvocationDiagnostics diagnostics) {
        log.info("Attempting dynamic prefetch template resolution for service: {}", config.getId());
        try {
            PrefetchResolver.ResolutionResult result = prefetchResolver.resolveWithStatus(config.getPrefetch(), request);
            diagnostics.getPrefetchStatus().addAll(result.getStatuses());

            Map<String, Resource> resolvedResources = result.getResources();
            if (resolvedResources.isEmpty()) {
                return null;
            }
            String pid = request.getContext() != null ? request.getContext().getPatientId() : null;
            List<Resource> resources = new ArrayList<>(resolvedResources.values());
            if (pid != null) {
                Reference patRef = new Reference("Patient/" + pid);
                for (Resource r : resources) {
                    ensureSubjectReference(r, patRef);
                }
            }
            log.info("Built prefetch provider from {} resolved templates", resolvedResources.size());
            return new PrefetchRetrieveProvider(resources, pid);
        } catch (Exception e) {
            log.warn("Prefetch template resolution failed, falling back to FHIR server: {}", e.getMessage());
            return null;
        }
    }

    /** Feature 4: collect non-fatal context warnings. */
    private List<String> collectContextWarnings(CdsRequest request, RetrieveProvider prefetchProvider) {
        List<String> warnings = new ArrayList<>();
        if (request.getContext() == null || request.getContext().getPatientId() == null) {
            warnings.add("context.patientId is missing — CQL Patient-context expressions will evaluate against an unknown patient");
            return warnings;
        }
        String pid = request.getContext().getPatientId();
        if (prefetchProvider instanceof PrefetchRetrieveProvider prp && !prp.hasPatient(pid)) {
            warnings.add("No Patient resource with id '" + pid + "' found in prefetch — retrieves filtered by patient context will return empty");
        }
        return warnings;
    }

    /** Feature 5: categorize FHIR server errors for diagnostic clarity. */
    private static void categorizeFhirServerError(CdsResponse.FhirServerDiagnostics diag, Throwable t) {
        diag.setStatus("error");
        diag.setErrorMessage(t.getMessage());
        String cls = t.getClass().getSimpleName();
        String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
        if (cls.contains("Timeout") || msg.contains("timeout") || msg.contains("timed out")) {
            diag.setErrorCategory("timeout");
        } else if (cls.contains("ResourceNotFound") || msg.contains("404") || msg.contains("not found")) {
            diag.setErrorCategory("not_found");
            diag.setHttpStatus(404);
        } else if (cls.contains("Unauthorized") || msg.contains("401") || msg.contains("unauthorized")) {
            diag.setErrorCategory("unauthorized");
            diag.setHttpStatus(401);
        } else if (cls.contains("Forbidden") || msg.contains("403")) {
            diag.setErrorCategory("forbidden");
            diag.setHttpStatus(403);
        } else if (cls.contains("Connect") || msg.contains("connection") || msg.contains("unreachable")) {
            diag.setErrorCategory("network");
        } else if (msg.contains("500") || cls.contains("InternalServer")) {
            diag.setErrorCategory("server_error");
            diag.setHttpStatus(500);
        } else {
            diag.setErrorCategory("other");
        }
    }

    /** Accumulator for debug diagnostics. */
    private static class InvocationDiagnostics {
        @lombok.Getter
        private final List<CdsResponse.PrefetchStatus> prefetchStatus = new ArrayList<>();
        @lombok.Getter
        private final List<String> contextWarnings = new ArrayList<>();
        @lombok.Setter
        @lombok.Getter
        private CdsResponse.FhirServerDiagnostics fhirServerDiagnostics;
    }

    private CqlExecutionResponse executeCql(CqlExecutionRequest execRequest, RetrieveProvider prefetchProvider) {
        try {
            if (prefetchProvider != null) {
                // Keep the patientId so the CQL engine can establish a proper Patient
                // context. Without a patient context the engine cannot evaluate
                // Patient-context expressions and all retrieves return empty.
                return executionService.executeWithProvider(execRequest, prefetchProvider);
            }
            return executionService.execute(execRequest);
        } catch (Exception e) {
            Phase phase = looksLikeTranslationError(e) ? Phase.CQL_TRANSLATION : Phase.CQL_EXECUTION;
            throw new CdsInvocationException(phase, e);
        }
    }

    private CdsResponse buildCards(CdsHooksService.CdsServiceConfig config, CqlExecutionResponse execResponse) {
        try {
            CardGenerationStrategy strategy = selectStrategy(config);
            return strategy.buildResponse(config, execResponse);
        } catch (Exception e) {
            throw new CdsInvocationException(Phase.CARD_GENERATION, e);
        }
    }

    private static boolean looksLikeTranslationError(Throwable t) {
        String cls = t.getClass().getSimpleName();
        return cls.contains("Translation") || cls.contains("Parse") || cls.contains("Syntax");
    }

    private static CdsResponse.CdsErrorInfo buildErrorInfo(Phase phase, Throwable t) {
        List<String> frames = Arrays.stream(t.getStackTrace())
                .filter(f -> f.getClassName().startsWith("com.cqlplatform"))
                .limit(5)
                .map(StackTraceElement::toString)
                .toList();
        return CdsResponse.CdsErrorInfo.builder()
                .phase(phase.name().toLowerCase())
                .errorType(t.getClass().getSimpleName())
                .message(t.getMessage())
                .stackTraceSummary(frames.isEmpty() ? null : frames)
                .build();
    }

    private static Map<String, Object> buildInvocationContext(CdsHooksService.CdsServiceConfig config, CdsRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("serviceId", config.getId());
        ctx.put("hook", config.getHook());
        if (request.getContext() != null) {
            CdsRequest.CdsContext c = request.getContext();
            if (c.getPatientId() != null) ctx.put("patientId", c.getPatientId());
            if (c.getUserId() != null) ctx.put("userId", c.getUserId());
            if (c.getEncounterId() != null) ctx.put("encounterId", c.getEncounterId());
        }
        if (request.getFhirServer() != null) ctx.put("fhirServer", request.getFhirServer());
        if (request.getPrefetch() != null) ctx.put("prefetchKeys", new ArrayList<>(request.getPrefetch().keySet()));
        return ctx;
    }

    private CardGenerationStrategy selectStrategy(CdsHooksService.CdsServiceConfig config) {
        if ("plan_definition".equals(config.getCardGenerationMode())
                && config.getPlanDefinitionJson() != null) {
            return planDefinitionStrategy;
        }
        return tupleStrategy;
    }

    private RetrieveProvider buildPrefetchProvider(CdsRequest request, InvocationDiagnostics diagnostics) {
        if (request.getPrefetch() == null || request.getPrefetch().isEmpty()) {
            log.info("No prefetch data provided, will use FHIR server");
            return null;
        }

        String patientId = request.getContext() != null ? request.getContext().getPatientId() : null;
        List<Resource> resources = new ArrayList<>();
        IParser jsonParser = fhirContext.newJsonParser();

        for (Map.Entry<String, Object> entry : request.getPrefetch().entrySet()) {
            long start = System.currentTimeMillis();
            int before = resources.size();
            try {
                String json = objectMapper.writeValueAsString(entry.getValue());
                Resource resource = (Resource) jsonParser.parseResource(json);

                if (resource instanceof Bundle bundle) {
                    if (bundle.hasEntry()) {
                        for (Bundle.BundleEntryComponent bundleEntry : bundle.getEntry()) {
                            if (bundleEntry.hasResource()) {
                                resources.add(bundleEntry.getResource());
                            }
                        }
                    }
                } else {
                    resources.add(resource);
                }
                log.info("Parsed prefetch key '{}': {}", entry.getKey(), resource.fhirType());
                diagnostics.getPrefetchStatus().add(CdsResponse.PrefetchStatus.builder()
                        .key(entry.getKey()).status("success")
                        .count(resources.size() - before)
                        .elapsedMs(System.currentTimeMillis() - start)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to parse prefetch key '{}': {}", entry.getKey(), e.getMessage());
                diagnostics.getPrefetchStatus().add(CdsResponse.PrefetchStatus.builder()
                        .key(entry.getKey()).status("failed").count(0)
                        .elapsedMs(System.currentTimeMillis() - start)
                        .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .build());
            }
        }

        if (resources.isEmpty()) {
            log.info("No usable resources found in prefetch data");
            return null;
        }

        // Auto-populate subject references for sandbox/prefetch resources that lack them.
        // The CQL engine filters retrieve results by patient context (e.g. Observation.subject),
        // so resources without a subject reference would be silently excluded.
        if (patientId != null) {
            Reference patientRef = new Reference("Patient/" + patientId);
            for (Resource resource : resources) {
                ensureSubjectReference(resource, patientRef);
            }
        }

        // Parse draftOrders from context and merge into resources
        Object draftOrdersData = request.getContext() != null ? request.getContext().getDraftOrders() : null;
        List<Resource> draftResources = parseContextBundle(draftOrdersData, "draftOrders");
        if (!draftResources.isEmpty()) {
            if (patientId != null) {
                Reference patRef = new Reference("Patient/" + patientId);
                for (Resource r : draftResources) {
                    ensureSubjectReference(r, patRef);
                }
            }
            resources.addAll(draftResources);
        }

        // Parse appointments from context and merge into resources
        Object appointmentsData = request.getContext() != null ? request.getContext().getAppointments() : null;
        List<Resource> appointmentResources = parseContextBundle(appointmentsData, "appointments");
        if (!appointmentResources.isEmpty()) {
            resources.addAll(appointmentResources);
        }

        log.info("Built prefetch provider with {} resources", resources.size());
        return new PrefetchRetrieveProvider(resources, patientId);
    }

    /**
     * Parses a FHIR Bundle (or single Resource) from a context object field.
     * Used for draftOrders, appointments, and any future Bundle-typed context fields.
     */
    private List<Resource> parseContextBundle(Object contextData, String label) {
        List<Resource> resources = new ArrayList<>();
        if (contextData == null) {
            return resources;
        }

        try {
            String json = objectMapper.writeValueAsString(contextData);
            IParser jsonParser = fhirContext.newJsonParser();
            Resource parsed = (Resource) jsonParser.parseResource(json);

            if (parsed instanceof Bundle bundle) {
                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.hasResource()) {
                            resources.add(entry.getResource());
                        }
                    }
                }
            } else {
                resources.add(parsed);
            }
            log.info("Parsed {} resources from {}", resources.size(), label);
        } catch (Exception e) {
            log.warn("Failed to parse {}: {}", label, e.getMessage());
        }

        return resources;
    }

    /**
     * Ensures a resource has a subject/patient reference so the CQL engine's
     * context filtering does not silently exclude it during evaluation.
     */
    private void ensureSubjectReference(Resource resource, Reference patientRef) {
        if (resource instanceof Observation obs) {
            if (!obs.hasSubject()) obs.setSubject(patientRef);
        } else if (resource instanceof Condition cond) {
            if (!cond.hasSubject()) cond.setSubject(patientRef);
        } else if (resource instanceof Procedure proc) {
            if (!proc.hasSubject()) proc.setSubject(patientRef);
        } else if (resource instanceof MedicationRequest medReq) {
            if (!medReq.hasSubject()) medReq.setSubject(patientRef);
        } else if (resource instanceof MedicationStatement medStmt) {
            if (!medStmt.hasSubject()) medStmt.setSubject(patientRef);
        } else if (resource instanceof Encounter enc) {
            if (!enc.hasSubject()) enc.setSubject(patientRef);
        } else if (resource instanceof AllergyIntolerance allergy) {
            if (!allergy.hasPatient()) allergy.setPatient(patientRef);
        } else if (resource instanceof Immunization imm) {
            if (!imm.hasPatient()) imm.setPatient(patientRef);
        } else if (resource instanceof DiagnosticReport diag) {
            if (!diag.hasSubject()) diag.setSubject(patientRef);
        } else if (resource instanceof ServiceRequest svcReq) {
            if (!svcReq.hasSubject()) svcReq.setSubject(patientRef);
        } else if (resource instanceof CarePlan carePlan) {
            if (!carePlan.hasSubject()) carePlan.setSubject(patientRef);
        } else if (resource instanceof Goal goal) {
            if (!goal.hasSubject()) goal.setSubject(patientRef);
        }
    }
}
