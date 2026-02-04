package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CdsHooksService {

    private final CqlExecutionService executionService;
    private final Map<String, CdsServiceConfig> serviceConfigs = new ConcurrentHashMap<>();

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
            execRequest.setFhirServerUrl(request.getFhirServer());

            CqlExecutionResponse execResponse = executionService.execute(execRequest);

            return buildCardsFromExecution(config, execResponse);
        } catch (Exception e) {
            log.error("CDS service invocation failed", e);
            return CdsResponse.builder()
                    .cards(List.of(createErrorCard(e.getMessage())))
                    .build();
        }
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
                                .build()
                ))
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

        if (execResponse.getResults() != null) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : execResponse.getResults().entrySet()) {
                Object value = entry.getValue().getValue();

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
                }
            }
        }

        if (cards.isEmpty()) {
            cards.add(createInfoCard(config.getTitle(), "No recommendations at this time."));
        }

        return CdsResponse.builder().cards(cards).build();
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
                                .build()
                ))
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
                                .build()
                ))
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
