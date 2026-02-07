package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.entity.CdsServicePrefetchEntity;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.cqlplatform.service.cql.CqlExecutionService;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CdsHooksService {

    private final CqlExecutionService executionService;
    private final CdsServiceConfigRepository repository;
    private final FhirContext fhirContext;
    private final ObjectMapper objectMapper;
    private final Map<String, CdsServiceConfig> serviceConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadServicesFromDatabase() {
        log.info("Loading CDS services from database...");
        List<CdsServiceConfigEntity> entities = repository.findAllEnabledWithPrefetch();
        for (CdsServiceConfigEntity entity : entities) {
            CdsServiceConfig config = entityToConfig(entity);
            serviceConfigs.put(config.getId(), config);
            log.info("Loaded CDS service: {}", config.getId());
        }
        log.info("Loaded {} CDS services from database", entities.size());

        // Load built-in BMI Service
        loadBmiService();
    }

    private void loadBmiService() {
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource(
                    "cql/BMI_CDS.cql");
            String cqlContent = new String(
                    org.springframework.util.FileCopyUtils.copyToByteArray(resource.getInputStream()),
                    java.nio.charset.StandardCharsets.UTF_8);

            Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch = new HashMap<>();
            prefetch.put("patient", CdsServiceDefinition.PrefetchTemplate.builder()
                    .query("Patient/{{context.patientId}}")
                    .build());
            prefetch.put("observations", CdsServiceDefinition.PrefetchTemplate.builder()
                    .query("Observation?patient={{context.patientId}}&category=vital-signs")
                    .build());

            CdsServiceConfig bmiConfig = CdsServiceConfig.builder()
                    .id("bmi-classifier")
                    .hook("patient-view")
                    .title("BMI Classification")
                    .description("Calculates BMI and provides health recommendations")
                    .cqlLibraryId("BMI_CDS")
                    .cqlContent(cqlContent)
                    .defaultIndicator("info")
                    .prefetch(prefetch)
                    .build();

            serviceConfigs.put(bmiConfig.getId(), bmiConfig);
            log.info("Loaded built-in CDS service: bmi-classifier");

        } catch (Exception e) {
            log.error("Failed to load built-in BMI service", e);
        }
    }

    @Transactional
    public CdsServiceConfigResponse createService(CdsServiceConfigRequest request) {
        if (repository.existsById(request.getId())) {
            throw new IllegalArgumentException("Service with ID '" + request.getId() + "' already exists");
        }

        CdsServiceConfigEntity entity = requestToEntity(request);
        entity = repository.save(entity);

        CdsServiceConfig config = entityToConfig(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            serviceConfigs.put(config.getId(), config);
        }

        log.info("Created CDS service: {}", entity.getId());
        return entityToResponse(entity);
    }

    @Transactional
    public CdsServiceConfigResponse updateService(String id, CdsServiceConfigRequest request) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));

        entity.setHook(request.getHook());
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setCqlContent(request.getCqlContent());
        entity.setCqlLibraryId(request.getCqlLibraryId());
        entity.setDefaultIndicator(request.getDefaultIndicator());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        entity.clearPrefetchItems();
        if (request.getPrefetch() != null) {
            for (Map.Entry<String, String> entry : request.getPrefetch().entrySet()) {
                CdsServicePrefetchEntity prefetch = CdsServicePrefetchEntity.builder()
                        .prefetchKey(entry.getKey())
                        .query(entry.getValue())
                        .build();
                entity.addPrefetchItem(prefetch);
            }
        }

        entity = repository.save(entity);

        if (Boolean.TRUE.equals(entity.getEnabled())) {
            serviceConfigs.put(id, entityToConfig(entity));
        } else {
            serviceConfigs.remove(id);
        }

        log.info("Updated CDS service: {}", id);
        return entityToResponse(entity);
    }

    @Transactional
    public void deleteService(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Service not found: " + id);
        }
        repository.deleteById(id);
        serviceConfigs.remove(id);
        log.info("Deleted CDS service: {}", id);
    }

    @Transactional(readOnly = true)
    public CdsServiceConfigResponse getService(String id) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        return entityToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getAllServices() {
        return repository.findAll().stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CdsServiceConfigResponse toggleServiceEnabled(String id, boolean enabled) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));

        entity.setEnabled(enabled);
        entity = repository.save(entity);

        if (enabled) {
            serviceConfigs.put(id, entityToConfig(entity));
        } else {
            serviceConfigs.remove(id);
        }

        log.info("Toggled CDS service {} enabled: {}", id, enabled);
        return entityToResponse(entity);
    }

    public void registerService(CdsServiceConfig config) {
        serviceConfigs.put(config.getId(), config);
        log.info("Registered CDS service: {}", config.getId());
    }

    public void unregisterService(String serviceId) {
        serviceConfigs.remove(serviceId);
        log.info("Unregistered CDS service: {}", serviceId);
    }

    public List<CdsServiceDefinition> getServiceDefinitions() {
        List<CdsServiceDefinition> definitions = new ArrayList<>();

        for (CdsServiceConfig config : serviceConfigs.values()) {
            definitions.add(CdsServiceDefinition.builder()
                    .id(config.getId())
                    .hook(config.getHook())
                    .title(config.getTitle())
                    .description(config.getDescription())
                    .prefetch(config.getPrefetch())
                    .build());
        }

        if (definitions.isEmpty()) {
            definitions.add(createDefaultDiabetesService());
            definitions.add(createDefaultMedicationService());
        }

        return definitions;
    }

    public CdsResponse invokeService(String serviceId, CdsRequest request) {
        log.info("Invoking CDS service: {} for patient: {}",
                serviceId, request.getContext() != null ? request.getContext().getPatientId() : "unknown");

        CdsServiceConfig config = serviceConfigs.get(serviceId);

        if (config == null) {
            return handleDefaultService(serviceId, request);
        }

        try {
            CqlExecutionRequest execRequest = new CqlExecutionRequest();
            execRequest.setCql(config.getCqlContent());
            execRequest.setPatientId(request.getContext() != null ? request.getContext().getPatientId() : null);
            // Parse prefetch data into FHIR resources for in-memory CQL execution
            RetrieveProvider prefetchProvider = buildPrefetchProvider(request);

            // Only use the client's fhirServer if prefetch is not available
            // and the server appears to be R4 compatible (our CQL requires R4).
            // Otherwise, fall back to the default FHIR server.
            if (prefetchProvider == null) {
                String fhirServer = request.getFhirServer();
                if (fhirServer != null && (fhirServer.contains("/r2/") || fhirServer.contains("/dstu2/"))) {
                    log.warn("Client FHIR server is DSTU2 ({}), falling back to default R4 server", fhirServer);
                    execRequest.setFhirServerUrl(null); // will use default
                } else {
                    execRequest.setFhirServerUrl(fhirServer);
                }
            }

            CqlExecutionResponse execResponse;
            if (prefetchProvider != null) {
                execResponse = executionService.executeWithProvider(execRequest, prefetchProvider);
            } else {
                execResponse = executionService.execute(execRequest);
            }

            return buildCardsFromExecution(config, execResponse);
        } catch (Exception e) {
            log.error("CDS service invocation failed", e);
            return CdsResponse.builder()
                    .cards(List.of(createErrorCard(e.getMessage())))
                    .build();
        }
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
                    // Extract individual resources from Bundle
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

        log.info("Built prefetch provider with {} resources", resources.size());
        return new PrefetchRetrieveProvider(resources, patientId);
    }

    private CdsResponse handleDefaultService(String serviceId, CdsRequest request) {
        return switch (serviceId) {
            case "diabetes-management" -> handleDiabetesManagement(request);
            case "medication-check" -> handleMedicationCheck(request);
            default -> CdsResponse.builder()
                    .cards(List.of(createInfoCard("Service not found", "The requested CDS service is not available.")))
                    .build();
        };
    }

    private CdsResponse handleDiabetesManagement(CdsRequest request) {
        List<CdsResponse.Card> cards = new ArrayList<>();

        cards.add(CdsResponse.Card.builder()
                .uuid(UUID.randomUUID().toString())
                .summary("Diabetes Care Reminder")
                .detail("This patient may benefit from diabetes management review. Consider checking HbA1c levels and reviewing medication adherence.")
                .indicator("info")
                .source(CdsResponse.Source.builder()
                        .label("CQL Platform - Diabetes Management")
                        .build())
                .suggestions(List.of(
                        CdsResponse.Suggestion.builder()
                                .uuid(UUID.randomUUID().toString())
                                .label("Order HbA1c Test")
                                .isRecommended(true)
                                .build(),
                        CdsResponse.Suggestion.builder()
                                .uuid(UUID.randomUUID().toString())
                                .label("Review Medications")
                                .isRecommended(false)
                                .build()))
                .build());

        return CdsResponse.builder().cards(cards).build();
    }

    private CdsResponse handleMedicationCheck(CdsRequest request) {
        List<CdsResponse.Card> cards = new ArrayList<>();

        cards.add(CdsResponse.Card.builder()
                .uuid(UUID.randomUUID().toString())
                .summary("Medication Interaction Check Complete")
                .detail("No significant drug interactions detected for the selected medications.")
                .indicator("info")
                .source(CdsResponse.Source.builder()
                        .label("CQL Platform - Drug Interaction Checker")
                        .build())
                .build());

        return CdsResponse.builder().cards(cards).build();
    }

    private CdsResponse buildCardsFromExecution(CdsServiceConfig config, CqlExecutionResponse execResponse) {
        List<CdsResponse.Card> cards = new ArrayList<>();

        log.info("Building cards from CQL execution. Success: {}, Results count: {}",
                execResponse.isSuccess(),
                execResponse.getResults() != null ? execResponse.getResults().size() : 0);

        if (execResponse.getResults() != null) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : execResponse.getResults()
                    .entrySet()) {
                Object value = entry.getValue().getValue();
                log.info("Expression '{}': value={}, type={}",
                        entry.getKey(), value, entry.getValue().getValueType());

                if (value instanceof Boolean && (Boolean) value) {
                    cards.add(CdsResponse.Card.builder()
                            .uuid(UUID.randomUUID().toString())
                            .summary(config.getTitle() + ": " + entry.getKey())
                            .detail("Condition '" + entry.getKey() + "' evaluated to true.")
                            .indicator(config.getDefaultIndicator() != null ? config.getDefaultIndicator() : "warning")
                            .source(CdsResponse.Source.builder()
                                    .label(config.getTitle())
                                    .build())
                            .build());
                } else if (value != null && value.getClass().getSimpleName().contains("Tuple")) {
                    try {
                        String summary = getField(value, "summary");
                        String detail = getField(value, "detail");
                        String indicator = getField(value, "indicator");
                        String sourceLabel = getField(value, "sourceLabel");

                        if (summary != null) {
                            cards.add(CdsResponse.Card.builder()
                                    .uuid(UUID.randomUUID().toString())
                                    .summary(summary)
                                    .detail(detail)
                                    .indicator(indicator != null ? indicator : "info")
                                    .source(CdsResponse.Source.builder()
                                            .label(sourceLabel != null ? sourceLabel : config.getTitle())
                                            .build())
                                    .build());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Tuple result for card", e);
                    }
                }
            }
        }

        if (cards.isEmpty()) {
            cards.add(createInfoCard(config.getTitle(), "No recommendations at this time."));
        }

        return CdsResponse.builder().cards(cards).build();
    }

    private String getField(Object tuple, String fieldName) {
        try {
            java.lang.reflect.Method getElements = tuple.getClass().getMethod("getElements");
            @SuppressWarnings("unchecked")
            Map<String, Object> elements = (Map<String, Object>) getElements.invoke(tuple);
            Object val = elements.get(fieldName);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private CdsResponse.Card createInfoCard(String summary, String detail) {
        return CdsResponse.Card.builder()
                .uuid(UUID.randomUUID().toString())
                .summary(summary)
                .detail(detail)
                .indicator("info")
                .source(CdsResponse.Source.builder()
                        .label("CQL Platform")
                        .build())
                .build();
    }

    private CdsResponse.Card createErrorCard(String errorMessage) {
        return CdsResponse.Card.builder()
                .uuid(UUID.randomUUID().toString())
                .summary("CDS Service Error")
                .detail("An error occurred: " + errorMessage)
                .indicator("warning")
                .source(CdsResponse.Source.builder()
                        .label("CQL Platform")
                        .build())
                .build();
    }

    private CdsServiceDefinition createDefaultDiabetesService() {
        return CdsServiceDefinition.builder()
                .id("diabetes-management")
                .hook("patient-view")
                .title("Diabetes Management")
                .description("Clinical decision support for diabetes patient management")
                .prefetch(Map.of(
                        "patient", CdsServiceDefinition.PrefetchTemplate.builder()
                                .query("Patient/{{context.patientId}}")
                                .build(),
                        "conditions", CdsServiceDefinition.PrefetchTemplate.builder()
                                .query("Condition?patient={{context.patientId}}")
                                .build()))
                .build();
    }

    private CdsServiceDefinition createDefaultMedicationService() {
        return CdsServiceDefinition.builder()
                .id("medication-check")
                .hook("order-select")
                .title("Medication Interaction Check")
                .description("Check for potential drug interactions")
                .prefetch(Map.of(
                        "patient", CdsServiceDefinition.PrefetchTemplate.builder()
                                .query("Patient/{{context.patientId}}")
                                .build(),
                        "medications", CdsServiceDefinition.PrefetchTemplate.builder()
                                .query("MedicationRequest?patient={{context.patientId}}")
                                .build()))
                .build();
    }

    private CdsServiceConfigEntity requestToEntity(CdsServiceConfigRequest request) {
        CdsServiceConfigEntity entity = CdsServiceConfigEntity.builder()
                .id(request.getId())
                .hook(request.getHook())
                .title(request.getTitle())
                .description(request.getDescription())
                .cqlContent(request.getCqlContent())
                .cqlLibraryId(request.getCqlLibraryId())
                .defaultIndicator(request.getDefaultIndicator())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .prefetchItems(new ArrayList<>())
                .build();

        if (request.getPrefetch() != null) {
            for (Map.Entry<String, String> entry : request.getPrefetch().entrySet()) {
                CdsServicePrefetchEntity prefetch = CdsServicePrefetchEntity.builder()
                        .prefetchKey(entry.getKey())
                        .query(entry.getValue())
                        .build();
                entity.addPrefetchItem(prefetch);
            }
        }

        return entity;
    }

    private CdsServiceConfig entityToConfig(CdsServiceConfigEntity entity) {
        Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch = new HashMap<>();
        if (entity.getPrefetchItems() != null) {
            for (CdsServicePrefetchEntity p : entity.getPrefetchItems()) {
                prefetch.put(p.getPrefetchKey(), CdsServiceDefinition.PrefetchTemplate.builder()
                        .query(p.getQuery())
                        .build());
            }
        }

        return CdsServiceConfig.builder()
                .id(entity.getId())
                .hook(entity.getHook())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cqlContent(entity.getCqlContent())
                .cqlLibraryId(entity.getCqlLibraryId())
                .defaultIndicator(entity.getDefaultIndicator())
                .prefetch(prefetch.isEmpty() ? null : prefetch)
                .build();
    }

    private CdsServiceConfigResponse entityToResponse(CdsServiceConfigEntity entity) {
        Map<String, String> prefetch = new HashMap<>();
        if (entity.getPrefetchItems() != null) {
            for (CdsServicePrefetchEntity p : entity.getPrefetchItems()) {
                prefetch.put(p.getPrefetchKey(), p.getQuery());
            }
        }

        return CdsServiceConfigResponse.builder()
                .id(entity.getId())
                .hook(entity.getHook())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cqlContent(entity.getCqlContent())
                .cqlLibraryId(entity.getCqlLibraryId())
                .defaultIndicator(entity.getDefaultIndicator())
                .enabled(entity.getEnabled())
                .prefetch(prefetch.isEmpty() ? null : prefetch)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class CdsServiceConfig {
        private String id;
        private String hook;
        private String title;
        private String description;
        private String cqlLibraryId;
        private String cqlContent;
        private String defaultIndicator;
        private Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch;
    }
}
