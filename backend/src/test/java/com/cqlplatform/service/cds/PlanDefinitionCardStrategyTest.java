package com.cqlplatform.service.cds;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.cds.CdsResponse;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PlanDefinitionCardStrategyTest {

    private PlanDefinitionCardStrategy strategy;
    private FhirContext fhirContext;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        CdsResourceFormatter resourceFormatter = new CdsResourceFormatter();
        CdsValueFormatter valueFormatter = new CdsValueFormatter(resourceFormatter);
        strategy = new PlanDefinitionCardStrategy(fhirContext, valueFormatter);
    }

    private String buildPlanDefinitionJson(PlanDefinition planDef) {
        return fhirContext.newJsonParser().encodeResourceToString(planDef);
    }

    @Test
    void buildResponse_withApplicableAction_shouldGenerateCard() {
        PlanDefinition planDef = new PlanDefinition();
        planDef.setStatus(Enumerations.PublicationStatus.ACTIVE);

        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Recommend Screening");
        action.setDescription("Patient is due for annual screening.");
        action.setPriority(PlanDefinition.RequestPriority.ROUTINE);
        action.addCondition()
                .setKind(PlanDefinition.ActionConditionKind.APPLICABILITY)
                .setExpression(new Expression().setLanguage("text/cql").setExpression("IsDueForScreening"));

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Screening Service")
                .planDefinitionJson(planDefJson)
                .cardGenerationMode("plan_definition")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("IsDueForScreening", ExpressionResult.builder()
                .name("IsDueForScreening").value(true).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        CdsResponse.Card card = response.getCards().get(0);
        assertThat(card.getSummary()).isEqualTo("Recommend Screening");
        assertThat(card.getDetail()).contains("due for annual screening");
        assertThat(card.getIndicator()).isEqualTo("info");
    }

    @Test
    void buildResponse_conditionFalse_shouldNotGenerateCard() {
        PlanDefinition planDef = new PlanDefinition();
        planDef.setStatus(Enumerations.PublicationStatus.ACTIVE);

        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Recommend Screening");
        action.addCondition()
                .setKind(PlanDefinition.ActionConditionKind.APPLICABILITY)
                .setExpression(new Expression().setLanguage("text/cql").setExpression("IsDueForScreening"));

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Screening Service")
                .planDefinitionJson(planDefJson)
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("IsDueForScreening", ExpressionResult.builder()
                .name("IsDueForScreening").value(false).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getDetail()).contains("No recommendations");
    }

    @Test
    void buildResponse_urgentPriority_shouldMapToWarning() {
        PlanDefinition planDef = new PlanDefinition();
        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Urgent Alert");
        action.setPriority(PlanDefinition.RequestPriority.URGENT);
        // No condition = always applicable

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Alert Service")
                .planDefinitionJson(planDefJson)
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getIndicator()).isEqualTo("warning");
    }

    @Test
    void buildResponse_statPriority_shouldMapToCritical() {
        PlanDefinition planDef = new PlanDefinition();
        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Critical Alert");
        action.setPriority(PlanDefinition.RequestPriority.STAT);

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Alert Service")
                .planDefinitionJson(planDefJson)
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getIndicator()).isEqualTo("critical");
    }

    @Test
    void buildResponse_withDynamicValues_shouldIncludeInDetail() {
        PlanDefinition planDef = new PlanDefinition();
        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Risk Assessment");
        action.setDescription("Patient risk level assessed.");

        action.addDynamicValue()
                .setPath("RiskScore")
                .setExpression(new Expression().setLanguage("text/cql").setExpression("CalculatedRiskScore"));

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Risk Service")
                .planDefinitionJson(planDefJson)
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("CalculatedRiskScore", ExpressionResult.builder()
                .name("CalculatedRiskScore").value(85).valueType("Integer").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        CdsResponse.Card card = response.getCards().get(0);
        assertThat(card.getDetail()).contains("Patient risk level assessed");
        assertThat(card.getDetail()).contains("**RiskScore**: 85");
    }

    @Test
    void buildResponse_invalidPlanDefinitionJson_shouldReturnErrorCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Bad Config")
                .planDefinitionJson("{ invalid json }")
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getIndicator()).isEqualTo("warning");
    }

    @Test
    void buildResponse_nullPlanDefinitionJson_shouldReturnErrorCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("No Config")
                .planDefinitionJson(null)
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getDetail()).contains("Invalid PlanDefinition");
    }

    @Test
    void buildResponse_actionWithNoCondition_shouldAlwaysApply() {
        PlanDefinition planDef = new PlanDefinition();
        PlanDefinition.PlanDefinitionActionComponent action = planDef.addAction();
        action.setTitle("Always Show");
        action.setDescription("This always applies.");

        String planDefJson = buildPlanDefinitionJson(planDef);

        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Always Service")
                .planDefinitionJson(planDefJson)
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getSummary()).isEqualTo("Always Show");
    }
}
