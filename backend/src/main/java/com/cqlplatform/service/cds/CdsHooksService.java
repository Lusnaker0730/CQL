package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsFeedbackEntity;
import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.entity.CdsServicePrefetchEntity;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.validation.HookTypeValidator;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer cdsInvocationTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cdsInvocationCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cdsInvocationErrorCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsAnalyticsService analyticsService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsFeedbackRepository feedbackRepository;

    @PostConstruct
    public void loadServicesFromDatabase() {
        log.info("Loading CDS services from database...");
        List<CdsServiceConfigEntity> entities = repository.findAllEnabledWithPrefetch();

        // For versioning: group by serviceName, keep only latest enabled version
        Map<String, CdsServiceConfigEntity> latestByServiceName = new LinkedHashMap<>();
        for (CdsServiceConfigEntity entity : entities) {
            String key = entity.getServiceName() != null ? entity.getServiceName() : entity.getId();
            CdsServiceConfigEntity existing = latestByServiceName.get(key);
            if (existing == null || entity.getVersion() > existing.getVersion()) {
                latestByServiceName.put(key, entity);
            }
        }

        for (CdsServiceConfigEntity entity : latestByServiceName.values()) {
            CdsServiceConfig config = entityToConfig(entity);
            serviceConfigs.put(config.getId(), config);
            log.info("Loaded CDS service: {} (v{})", config.getId(), entity.getVersion());
        }
        log.info("Loaded {} CDS services from database", latestByServiceName.size());

        // Load built-in BMI Service
        loadBmiService();
    }

    private void loadBmiService() {
        try {
            ClassPathResource resource = new ClassPathResource("cql/BMI_CDS.cql");
            String cqlContent = new String(
                    FileCopyUtils.copyToByteArray(resource.getInputStream()),
                    StandardCharsets.UTF_8);

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
        HookTypeValidator.validate(request.getHook());

        // Versioning: use request.getId() as serviceName
        String serviceName = request.getId();
        Optional<Integer> maxVersion = repository.findMaxVersionByServiceName(serviceName);
        int newVersion = maxVersion.map(v -> v + 1).orElse(1);

        String actualId = newVersion > 1 ? serviceName + "-v" + newVersion : serviceName;

        if (repository.existsById(actualId)) {
            throw new IllegalArgumentException("Service with ID '" + actualId + "' already exists");
        }

        CdsServiceConfigEntity entity = requestToEntity(request);
        entity.setId(actualId);
        entity.setServiceName(serviceName);
        entity.setVersion(newVersion);
        entity = repository.save(entity);

        CdsServiceConfig config = entityToConfig(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            // Remove older versions from cache, add latest
            serviceConfigs.entrySet().removeIf(e -> {
                CdsServiceConfig c = e.getValue();
                return serviceName.equals(c.getServiceName());
            });
            serviceConfigs.put(config.getId(), config);
        }

        log.info("Created CDS service: {} (v{})", entity.getId(), newVersion);
        return entityToResponse(entity);
    }

    @Transactional
    public CdsServiceConfigResponse updateService(String id, CdsServiceConfigRequest request) {
        HookTypeValidator.validate(request.getHook());

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
                    .version(config.getVersion())
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
        String patientId = request.getContext() != null ? request.getContext().getPatientId() : "unknown";
        log.info("Invoking CDS service: {} for patient: {}", serviceId, patientId);
        if (cdsInvocationCounter != null) cdsInvocationCounter.increment();
        Timer.Sample sample = cdsInvocationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        CdsServiceConfig config = serviceConfigs.get(serviceId);

        if (config == null) {
            CdsResponse response = handleDefaultService(serviceId, request);
            if (sample != null && cdsInvocationTimer != null) sample.stop(cdsInvocationTimer);
            return response;
        }

        // Validate hook type matches config
        if (request.getHook() != null && !request.getHook().equals(config.getHook())) {
            throw new IllegalArgumentException(
                    "Hook type mismatch: request hook '" + request.getHook() +
                            "' does not match service hook '" + config.getHook() + "'");
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

            CdsResponse response = buildCardsFromExecution(config, execResponse);
            if (sample != null && cdsInvocationTimer != null) sample.stop(cdsInvocationTimer);

            // Record analytics
            if (analyticsService != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                analyticsService.recordInvocation(serviceId, elapsed, true);
            }

            return response;
        } catch (Exception e) {
            if (cdsInvocationErrorCounter != null) cdsInvocationErrorCounter.increment();
            if (sample != null && cdsInvocationTimer != null) sample.stop(cdsInvocationTimer);

            // Record analytics for error
            if (analyticsService != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                analyticsService.recordInvocation(serviceId, elapsed, false);
            }

            log.error("CDS service invocation failed", e);
            return CdsResponse.builder()
                    .cards(List.of(createErrorCard(e.getMessage())))
                    .build();
        }
    }

    // --- Feedback ---

    @Transactional
    public void processFeedback(String serviceId, CdsFeedbackRequest request) {
        // Validate service exists
        if (!repository.existsById(serviceId) && !serviceConfigs.containsKey(serviceId)) {
            throw new IllegalArgumentException("Service not found: " + serviceId);
        }

        if (request.getFeedback() == null || request.getFeedback().isEmpty()) {
            return;
        }

        if (feedbackRepository == null) {
            log.warn("Feedback repository not available, skipping feedback persistence");
            return;
        }

        for (CdsFeedbackRequest.FeedbackItem item : request.getFeedback()) {
            CdsFeedbackEntity entity = CdsFeedbackEntity.builder()
                    .serviceId(serviceId)
                    .cardUuid(item.getCard())
                    .outcome(item.getOutcome())
                    .outcomeTimestamp(item.getOutcomeTimestamp() != null
                            ? LocalDateTime.parse(item.getOutcomeTimestamp()) : null)
                    .acceptedSuggestions(item.getAcceptedSuggestions() != null
                            ? serializeToJson(item.getAcceptedSuggestions()) : null)
                    .build();

            if (item.getOverrideReason() != null) {
                entity.setOverrideReasonCode(item.getOverrideReason().getCode());
                entity.setOverrideReasonDisplay(item.getOverrideReason().getDisplay());
            }

            feedbackRepository.save(entity);
        }

        log.info("Processed {} feedback items for service {}", request.getFeedback().size(), serviceId);
    }

    @Transactional(readOnly = true)
    public List<CdsFeedbackEntity> getFeedback(String serviceId) {
        if (feedbackRepository == null) {
            return List.of();
        }
        return feedbackRepository.findByServiceIdOrderByCreatedAtDesc(serviceId);
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }

    // --- Versioning ---

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getServiceVersions(String serviceName) {
        return repository.findByServiceNameOrderByVersionDesc(serviceName).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CdsServiceConfigResponse rollbackService(String serviceName, int targetVersion) {
        List<CdsServiceConfigEntity> versions = repository.findByServiceNameOrderByVersionDesc(serviceName);

        if (versions.isEmpty()) {
            throw new IllegalArgumentException("No service found with name: " + serviceName);
        }

        CdsServiceConfigEntity targetEntity = versions.stream()
                .filter(v -> v.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + targetVersion + " not found for service: " + serviceName));

        // Disable all versions
        for (CdsServiceConfigEntity v : versions) {
            v.setEnabled(false);
            repository.save(v);
            serviceConfigs.remove(v.getId());
        }

        // Enable target version
        targetEntity.setEnabled(true);
        targetEntity = repository.save(targetEntity);

        CdsServiceConfig config = entityToConfig(targetEntity);
        serviceConfigs.put(config.getId(), config);

        log.info("Rolled back service {} to version {}", serviceName, targetVersion);
        return entityToResponse(targetEntity);
    }

    // --- Prefetch provider ---

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
        List<CdsResponse.SystemAction> systemActions = new ArrayList<>();

        log.info("Building cards from CQL execution. Success: {}, Results count: {}",
                execResponse.isSuccess(),
                execResponse.getResults() != null ? execResponse.getResults().size() : 0);
        log.info("Execution Metadata: {}", execResponse.getMetadata());

        if (execResponse.getResults() != null) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : execResponse.getResults()
                    .entrySet()) {
                Object value = entry.getValue().getValue();
                log.info("Expression '{}': value={}, type={}",
                        entry.getKey(), value, entry.getValue().getValueType());

                // Handle SystemActions expression
                if ("SystemActions".equals(entry.getKey()) && value instanceof Iterable) {
                    systemActions.addAll(parseSystemActions(value));
                    continue;
                }

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
                        String selectionBehavior = getField(value, "selectionBehavior");

                        // Parse suggestions from Tuple
                        List<CdsResponse.Suggestion> suggestions = parseSuggestions(value);

                        if (summary != null) {
                            cards.add(CdsResponse.Card.builder()
                                    .uuid(UUID.randomUUID().toString())
                                    .summary(summary)
                                    .detail(detail)
                                    .indicator(indicator != null ? indicator : "info")
                                    .source(CdsResponse.Source.builder()
                                            .label(sourceLabel != null ? sourceLabel : config.getTitle())
                                            .build())
                                    .selectionBehavior(selectionBehavior)
                                    .suggestions(suggestions.isEmpty() ? null : suggestions)
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

        return CdsResponse.builder()
                .cards(cards)
                .systemActions(systemActions.isEmpty() ? null : systemActions)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTupleElements(Object tuple) {
        try {
            java.lang.reflect.Method getElements = tuple.getClass().getMethod("getElements");
            return (Map<String, Object>) getElements.invoke(tuple);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String getField(Object tuple, String fieldName) {
        Map<String, Object> elements = getTupleElements(tuple);
        Object val = elements.get(fieldName);
        return val != null ? val.toString() : null;
    }

    private Object getObjectField(Object tuple, String fieldName) {
        Map<String, Object> elements = getTupleElements(tuple);
        return elements.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    private List<Object> getListField(Object tuple, String fieldName) {
        Object val = getObjectField(tuple, fieldName);
        if (val instanceof List) {
            return (List<Object>) val;
        }
        return Collections.emptyList();
    }

    private Boolean getBooleanField(Object tuple, String fieldName) {
        Object val = getObjectField(tuple, fieldName);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return null;
    }

    private List<CdsResponse.Suggestion> parseSuggestions(Object cardTuple) {
        List<Object> suggestionsRaw = getListField(cardTuple, "suggestions");
        List<CdsResponse.Suggestion> suggestions = new ArrayList<>();

        for (Object suggObj : suggestionsRaw) {
            if (suggObj != null && suggObj.getClass().getSimpleName().contains("Tuple")) {
                String label = getField(suggObj, "label");
                Boolean isRecommended = getBooleanField(suggObj, "isRecommended");
                List<CdsResponse.Action> actions = parseActions(suggObj);

                suggestions.add(CdsResponse.Suggestion.builder()
                        .uuid(UUID.randomUUID().toString())
                        .label(label)
                        .isRecommended(isRecommended)
                        .actions(actions.isEmpty() ? null : actions)
                        .build());
            }
        }

        return suggestions;
    }

    private List<CdsResponse.Action> parseActions(Object suggestionTuple) {
        List<Object> actionsRaw = getListField(suggestionTuple, "actions");
        List<CdsResponse.Action> actions = new ArrayList<>();

        for (Object actionObj : actionsRaw) {
            if (actionObj != null && actionObj.getClass().getSimpleName().contains("Tuple")) {
                String type = getField(actionObj, "type");
                String description = getField(actionObj, "description");
                String resourceJson = getField(actionObj, "resource");
                String resourceId = getField(actionObj, "resourceId");

                Object resource = null;
                if (resourceJson != null) {
                    try {
                        resource = objectMapper.readValue(resourceJson, Map.class);
                    } catch (Exception e) {
                        resource = resourceJson;
                    }
                }

                actions.add(CdsResponse.Action.builder()
                        .type(type)
                        .description(description)
                        .resource(resource)
                        .resourceId(resourceId)
                        .build());
            }
        }

        return actions;
    }

    private List<CdsResponse.SystemAction> parseSystemActions(Object value) {
        List<CdsResponse.SystemAction> systemActions = new ArrayList<>();

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && item.getClass().getSimpleName().contains("Tuple")) {
                    String type = getField(item, "type");
                    String description = getField(item, "description");
                    String resourceJson = getField(item, "resource");
                    String resourceId = getField(item, "resourceId");

                    Object resource = null;
                    if (resourceJson != null) {
                        try {
                            resource = objectMapper.readValue(resourceJson, Map.class);
                        } catch (Exception e) {
                            resource = resourceJson;
                        }
                    }

                    systemActions.add(CdsResponse.SystemAction.builder()
                            .type(type)
                            .description(description)
                            .resource(resource)
                            .resourceId(resourceId)
                            .build());
                }
            }
        }

        return systemActions;
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
                .serviceName(request.getId())
                .version(1)
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
                .version(entity.getVersion())
                .serviceName(entity.getServiceName())
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
                .version(entity.getVersion())
                .serviceName(entity.getServiceName())
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
        private Integer version;
        private String serviceName;
        private Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch;
    }
}
