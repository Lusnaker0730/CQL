package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.CqlGenerationException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.ecqm.PublishResult;
import com.cqlplatform.repository.EcqmArtifactRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcqmPublishServiceTest {

    @Mock
    private EcqmArtifactRepository ecqmRepository;

    @Mock
    private MeasureDefinitionRepository measureRepository;

    @Mock
    private EcqmCqlBuilder ecqmCqlBuilder;

    @Mock
    private EcqmCqlGenerationService cqlGenerationService;

    @Mock
    private com.cqlplatform.repository.TenantRepository tenantRepository;

    @InjectMocks
    private EcqmPublishService publishService;

    @org.junit.jupiter.api.BeforeEach
    void setTenant() {
        com.cqlplatform.security.TenantContext.setCurrentTenantId(7L);
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.cqlplatform.security.TenantContext.clear();
    }

    // ===== Helpers =====

    private EcqmArtifactEntity createEcqmEntity(Long id, String name, String owner) {
        return EcqmArtifactEntity.builder()
                .id(id)
                .name(name)
                .version("1.0.0")
                .description("Test eCQM")
                .status("draft")
                .fhirVersion("4.0.1")
                .scoringType("proportion")
                .populationBasis("boolean")
                .improvementNotation("increase")
                .ownerUsername(owner)
                .populationGroupsList(new ArrayList<>())
                .baseElementsList(new ArrayList<>())
                .parametersList(new ArrayList<>())
                .supplementalDataList(new ArrayList<>())
                .stratifiersList(new ArrayList<>())
                .build();
    }

    // ===== publish — success =====

    private CqlTranslationResponse successfulValidation() {
        return CqlTranslationResponse.builder()
                .success(true)
                .errors(List.of())
                .build();
    }

    @Test
    void publish_newMeasure_shouldCreateMeasureDefinition() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        entity.setPublishedMeasureId(null);

        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));
        when(cqlGenerationService.validateCql(1L)).thenReturn(successfulValidation());
        when(ecqmCqlBuilder.buildEcqmCql(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new CqlBuildResult("library MyMeasure version '1.0.0'\n", List.of()));
        when(measureRepository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(ecqmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PublishResult result = publishService.publish(1L, "testuser");

        assertThat(result.getMeasureDefinitionId()).isEqualTo(100L);
        assertThat(result.getMeasureName()).isEqualTo("MyMeasure");
        assertThat(result.getCql()).contains("library MyMeasure");
        assertThat(result.getMessage()).contains("published successfully");

        verify(measureRepository).save(any(MeasureDefinitionEntity.class));
        verify(ecqmRepository).save(argThat(e -> e.getPublishedMeasureId() == 100L && "active".equals(e.getStatus())));
    }

    // PAT-233 — the workspace's (artifact-level) stratifiers must reach the MeasureDefinition,
    // on every group, with the unsuffixed define the CQL builder emits for them; group-level
    // ones keep their per-group suffix. Before, only group-level ones were mapped and no UI
    // edits those, so a stratifier built in the workspace never reached evaluation.
    @Test
    void publish_mapsArtifactLevelStratifiersOntoEveryGroup_andKeepsKindAndDescription() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        Map<String, Object> group1 = new LinkedHashMap<>(Map.of("groupId", "g1", "populations", Map.of(),
                "stratifiers", List.of(Map.of("stratifierId", "local", "criteria", Map.of()))));
        Map<String, Object> group2 = new LinkedHashMap<>(Map.of("groupId", "g2", "populations", Map.of()));
        entity.setPopulationGroupsList(new ArrayList<>(List.of(group1, group2)));
        entity.setStratifiersList(new ArrayList<>(List.of(
                Map.of("stratifierId", "sex", "kind", "value", "description", "By sex",
                        "value", Map.of("source", "gender")),
                Map.of("stratifierId", "elderly", "criteria", Map.of()))));

        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));
        when(cqlGenerationService.validateCql(1L)).thenReturn(successfulValidation());
        when(ecqmCqlBuilder.buildEcqmCql(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new CqlBuildResult("library MyMeasure version '1.0.0'\n", List.of()));
        when(measureRepository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(ecqmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publishService.publish(1L, "testuser");

        org.mockito.ArgumentCaptor<MeasureDefinitionEntity> saved = org.mockito.ArgumentCaptor.forClass(MeasureDefinitionEntity.class);
        verify(measureRepository).save(saved.capture());
        List<com.cqlplatform.model.measure.GroupDefinition> groups = saved.getValue().getGroupDefinitionList();
        assertThat(groups).hasSize(2);

        assertThat(groups.get(0).getStratifiers())
                .extracting(s -> s.getStratifierId() + "|" + s.getCriteriaExpression() + "|" + s.getKind() + "|" + s.getDescription())
                .containsExactly("local|Stratifier local 1|criteria|null",
                        "sex|Stratifier sex|value|By sex",
                        "elderly|Stratifier elderly|criteria|null");
        assertThat(groups.get(1).getStratifiers())
                .extracting(com.cqlplatform.model.measure.StratifierDefinition::getCriteriaExpression)
                .containsExactly("Stratifier sex", "Stratifier elderly");
        assertThat(groups.get(0).getStratifiers().get(1).isValueBased()).isTrue();
    }

    // PAT-235 — a multi-component stratifier maps to one define per component; the stratifier
    // itself has no criteria expression.
    @Test
    void publish_mapsComponentStratifiers_withOneDefinePerComponent() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        entity.setPopulationGroupsList(new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("groupId", "g1", "populations", Map.of())),
                new LinkedHashMap<>(Map.of("groupId", "g2", "populations", Map.of())))));
        entity.setStratifiersList(new ArrayList<>(List.of(Map.of("stratifierId", "sex-age", "description", "Sex by age",
                "components", List.of(
                        Map.of("code", "sex", "kind", "value", "value", Map.of("source", "gender")),
                        Map.of("code", "age", "kind", "value", "description", "Age band", "value", Map.of("source", "ageBands")))))));

        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));
        when(cqlGenerationService.validateCql(1L)).thenReturn(successfulValidation());
        when(ecqmCqlBuilder.buildEcqmCql(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new CqlBuildResult("library MyMeasure version '1.0.0'\n", List.of()));
        when(measureRepository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(ecqmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publishService.publish(1L, "testuser");

        org.mockito.ArgumentCaptor<MeasureDefinitionEntity> saved = org.mockito.ArgumentCaptor.forClass(MeasureDefinitionEntity.class);
        verify(measureRepository).save(saved.capture());
        com.cqlplatform.model.measure.StratifierDefinition strat = saved.getValue().getGroupDefinitionList().get(1).getStratifiers().get(0);
        assertThat(strat.getStratifierId()).isEqualTo("sex-age");
        assertThat(strat.getCriteriaExpression()).isNull();
        assertThat(strat.hasComponents()).isTrue();
        assertThat(strat.getComponents()).extracting(c -> c.getCode() + "|" + c.getCriteriaExpression() + "|" + c.getKind() + "|" + c.getDescription())
                .containsExactly("sex|Stratifier sex-age sex|value|null", "age|Stratifier sex-age age|value|Age band");
    }

    // PAT-234 — the workspace's SDE elements become the measure's declared supplemental data /
    // risk adjustment factors (by usage). Before, publish carried none of them, so neither the
    // evaluation nor the exchange package knew the measure had any.
    @Test
    void publish_mapsSdeElementsToSupplementalDataAndRiskAdjustmentsByUsage() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        entity.setSupplementalDataList(new ArrayList<>(List.of(
                Map.of("name", "SDE Sex"),
                Map.of("name", "RAF Age Band", "usage", "risk-adjustment-factor", "description", "Age at period end",
                        "kind", "value", "value", Map.of("source", "ageBands")),
                Map.of("name", "Custom Flag", "usage", "supplemental-data", "criteria", Map.of()),
                Map.of("name", "   "))));

        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));
        when(cqlGenerationService.validateCql(1L)).thenReturn(successfulValidation());
        when(ecqmCqlBuilder.buildEcqmCql(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new CqlBuildResult("library MyMeasure version '1.0.0'\n", List.of()));
        when(measureRepository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(ecqmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publishService.publish(1L, "testuser");

        org.mockito.ArgumentCaptor<MeasureDefinitionEntity> saved = org.mockito.ArgumentCaptor.forClass(MeasureDefinitionEntity.class);
        verify(measureRepository).save(saved.capture());
        assertThat(saved.getValue().getSupplementalDataList())
                .extracting(com.cqlplatform.model.measure.MeasureDefinition.SupplementalDataDef::getDefinition)
                .containsExactly("SDE Sex", "Custom Flag");
        assertThat(saved.getValue().getRiskAdjustmentList())
                .extracting(r -> r.getDefinition() + "|" + r.getDescription())
                .containsExactly("RAF Age Band|Age at period end");
    }

    @Test
    void publish_existingMeasure_shouldUpdateMeasureDefinition() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        entity.setPublishedMeasureId(50L);

        MeasureDefinitionEntity existingMeasure = MeasureDefinitionEntity.builder()
                .id(50L).name("OldName").build();

        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));
        when(cqlGenerationService.validateCql(1L)).thenReturn(successfulValidation());
        when(measureRepository.findByIdAndTenantId(50L, 7L)).thenReturn(Optional.of(existingMeasure));
        when(ecqmCqlBuilder.buildEcqmCql(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyList(), anyList(), anyList(), anyList(), anyString()))
                .thenReturn(new CqlBuildResult("library MyMeasure version '1.0.0'\n", List.of()));
        when(measureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ecqmRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PublishResult result = publishService.publish(1L, "testuser");

        assertThat(result.getMeasureDefinitionId()).isEqualTo(50L);
        verify(measureRepository).save(argThat(m -> "MyMeasure".equals(m.getName())));
    }

    // ===== publish — CQL validation failure =====

    @Test
    void publish_withCqlErrors_shouldThrow() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "testuser");
        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));

        CqlTranslationResponse failedResp = CqlTranslationResponse.builder()
                .success(false)
                .errors(List.of(
                        CqlTranslationResponse.CqlError.builder()
                                .severity("error").message("Could not resolve type").startLine(5).startColumn(1).build()
                ))
                .build();
        when(cqlGenerationService.validateCql(1L)).thenReturn(failedResp);

        assertThatThrownBy(() -> publishService.publish(1L, "testuser"))
                .isInstanceOf(CqlGenerationException.class)
                .hasMessageContaining("CQL validation failed");

        verify(measureRepository, never()).save(any());
    }

    // ===== publish — authorization =====

    @Test
    void publish_asNonOwner_shouldThrow() {
        EcqmArtifactEntity entity = createEcqmEntity(1L, "MyMeasure", "owner");
        when(ecqmRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> publishService.publish(1L, "hacker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== publish — not found =====

    @Test
    void publish_notFound_shouldThrow() {
        when(ecqmRepository.findByIdAndTenantId(999L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publishService.publish(999L, "testuser"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
