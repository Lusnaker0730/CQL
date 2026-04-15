package com.cqlplatform.service.cds;

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

    /**
     * Invoke a CDS service: execute CQL and generate cards.
     */
    public CdsResponse invoke(CdsHooksService.CdsServiceConfig config, CdsRequest request) {
        String patientId = request.getContext() != null ? request.getContext().getPatientId() : "unknown";
        log.info("Invoking CDS service: {} for patient: {}", config.getId(), patientId);
        if (cdsInvocationCounter != null)
            cdsInvocationCounter.increment();
        Timer.Sample sample = cdsInvocationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        try {
            CqlExecutionRequest execRequest = new CqlExecutionRequest();
            execRequest.setCql(config.getCqlContent());
            execRequest.setPatientId(request.getContext() != null ? request.getContext().getPatientId() : null);

            RetrieveProvider prefetchProvider = buildPrefetchProvider(request);

            // If no prefetch data, try dynamic resolution from prefetch templates
            if (prefetchProvider == null && prefetchResolver != null
                    && config.getPrefetch() != null && !config.getPrefetch().isEmpty()
                    && request.getFhirServer() != null && !request.getFhirServer().isBlank()) {
                log.info("Attempting dynamic prefetch template resolution for service: {}", config.getId());
                try {
                    Map<String, Resource> resolvedResources = prefetchResolver.resolve(config.getPrefetch(), request);
                    if (!resolvedResources.isEmpty()) {
                        String pid = request.getContext() != null ? request.getContext().getPatientId() : null;
                        List<Resource> resources = new ArrayList<>(resolvedResources.values());
                        // Auto-populate subject references
                        if (pid != null) {
                            Reference patRef = new Reference("Patient/" + pid);
                            for (Resource r : resources) {
                                ensureSubjectReference(r, patRef);
                            }
                        }
                        prefetchProvider = new PrefetchRetrieveProvider(resources, pid);
                        log.info("Built prefetch provider from {} resolved templates", resolvedResources.size());
                    }
                } catch (Exception e) {
                    log.warn("Prefetch template resolution failed, falling back to FHIR server: {}", e.getMessage());
                }
            }

            if (prefetchProvider == null) {
                String fhirServer = request.getFhirServer();
                if (fhirServer != null && (fhirServer.contains("/r2/") || fhirServer.contains("/dstu2/"))) {
                    log.warn("Client FHIR server is DSTU2 ({}), falling back to default R4 server", fhirServer);
                    execRequest.setFhirServerUrl(null);
                } else {
                    execRequest.setFhirServerUrl(fhirServer);
                }
            }

            CqlExecutionResponse execResponse;
            if (prefetchProvider != null) {
                // Keep the patientId so the CQL engine can establish a proper Patient
                // context.  Without a patient context the engine cannot evaluate
                // Patient-context expressions and all retrieves return empty.
                execResponse = executionService.executeWithProvider(execRequest, prefetchProvider);
            } else {
                execResponse = executionService.execute(execRequest);
            }

            // Select strategy based on card generation mode
            CardGenerationStrategy strategy = selectStrategy(config);
            CdsResponse response = strategy.buildResponse(config, execResponse);

            if (sample != null && cdsInvocationTimer != null)
                sample.stop(cdsInvocationTimer);

            if (analyticsService != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                analyticsService.recordInvocation(config.getId(), elapsed, true);
            }

            return response;
        } catch (Exception e) {
            if (cdsInvocationErrorCounter != null)
                cdsInvocationErrorCounter.increment();
            if (sample != null && cdsInvocationTimer != null)
                sample.stop(cdsInvocationTimer);

            if (analyticsService != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                analyticsService.recordInvocation(config.getId(), elapsed, false);
            }

            log.error("CDS service invocation failed", e);
            return CdsResponse.builder()
                    .cards(List.of(tupleStrategy.createErrorCard(e.getMessage())))
                    .build();
        }
    }

    private CardGenerationStrategy selectStrategy(CdsHooksService.CdsServiceConfig config) {
        if ("plan_definition".equals(config.getCardGenerationMode())
                && config.getPlanDefinitionJson() != null) {
            return planDefinitionStrategy;
        }
        return tupleStrategy;
    }

    private RetrieveProvider buildPrefetchProvider(CdsRequest request) {
        if (request.getPrefetch() == null || request.getPrefetch().isEmpty()) {
            log.info("No prefetch data provided, will use FHIR server");
            return null;
        }

        String patientId = request.getContext() != null ? request.getContext().getPatientId() : null;
        List<Resource> resources = new ArrayList<>();
        IParser jsonParser = fhirContext.newJsonParser();

        for (Map.Entry<String, Object> entry : request.getPrefetch().entrySet()) {
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
            } catch (Exception e) {
                log.warn("Failed to parse prefetch key '{}': {}", entry.getKey(), e.getMessage());
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
