package com.cqlplatform.service.authoring;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.authoring.ArtifactRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionTreeValidatorTest {

    @Mock
    private TemplateService templateService;

    @Mock
    private ModifierService modifierService;

    private ExpressionTreeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ExpressionTreeValidator(templateService, modifierService);
    }

    // ===== Element type / modifier ID validation =====

    @Test
    void validate_validTree_shouldPass() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId("BooleanExists")).thenReturn(true);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .expTreeInclude(Map.of(
                        "id", "And",
                        "childInstances", List.of(
                                Map.of("type", "GenericObservation_vsac",
                                        "name", "LDL",
                                        "modifiers", List.of(
                                                Map.of("id", "BooleanExists", "name", "Exists")
                                        ))
                        )
                ))
                .build();

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_unknownElementType_shouldThrowWithDetails() {
        when(templateService.isValidElementType("FakeTemplate")).thenReturn(false);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .expTreeInclude(Map.of(
                        "childInstances", List.of(
                                Map.of("type", "FakeTemplate", "name", "Bad Element")
                        )
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid expression tree elements")
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("FakeTemplate");
                    assertThat(ve.getDetails().get(0)).contains("expTreeInclude");
                });
    }

    @Test
    void validate_unknownModifierId_shouldThrowWithDetails() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId("NonExistentModifier")).thenReturn(false);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .expTreeExclude(Map.of(
                        "childInstances", List.of(
                                Map.of("type", "GenericObservation_vsac",
                                        "modifiers", List.of(
                                                Map.of("id", "NonExistentModifier", "name", "Bad Modifier")
                                        ))
                        )
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("NonExistentModifier");
                    assertThat(ve.getDetails().get(0)).contains("expTreeExclude");
                });
    }

    @Test
    void validate_multipleErrors_shouldCollectAll() {
        when(templateService.isValidElementType(anyString())).thenReturn(false);
        when(modifierService.isValidModifierId(anyString())).thenReturn(false);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .expTreeInclude(Map.of(
                        "childInstances", List.of(
                                Map.of("type", "BadType1"),
                                Map.of("type", "BadType2",
                                        "modifiers", List.of(
                                                Map.of("id", "BadMod1")
                                        ))
                        )
                ))
                .baseElements(List.of(
                        Map.of("type", "BadType3")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    // BadType1, BadType2, BadMod1, BadType3 = 4 errors
                    assertThat(ve.getDetails()).hasSize(4);
                });
    }

    @Test
    void validate_nullTrees_shouldPass() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Partial Update")
                .build();

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_conjunctionNode_shouldRecurseIntoChildren() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(templateService.isValidElementType("UnknownNested")).thenReturn(false);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .expTreeInclude(Map.of(
                        "childInstances", List.of(
                                Map.of("conjunction", true,
                                        "childInstances", List.of(
                                                Map.of("type", "GenericObservation_vsac"),
                                                Map.of("type", "UnknownNested")
                                        ))
                        )
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("UnknownNested");
                });
    }

    @Test
    void validate_subpopulations_shouldValidateNodes() {
        when(templateService.isValidElementType("FakeSub")).thenReturn(false);

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .subpopulations(List.of(
                        Map.of("type", "FakeSub")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).hasSize(1);
                    assertThat(ve.getDetails().get(0)).contains("subpopulations");
                    assertThat(ve.getDetails().get(0)).contains("FakeSub");
                });
    }

    // ===== Duplicate define name detection =====

    @Test
    void validate_duplicateBaseElementNames_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .baseElements(List.of(
                        Map.of("name", "LDL Cholesterol"),
                        Map.of("name", "LDL Cholesterol")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("baseElements") && d.contains("duplicate name") && d.contains("LDL Cholesterol"));
                });
    }

    @Test
    void validate_duplicateSubpopulationNames_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .subpopulations(List.of(
                        Map.of("subpopulationName", "Elderly"),
                        Map.of("subpopulationName", "Elderly")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("subpopulations") && d.contains("duplicate name") && d.contains("Elderly"));
                });
    }

    @Test
    void validate_duplicateParameterNames_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .parameters(List.of(
                        Map.of("name", "Threshold", "type", "integer"),
                        Map.of("name", "Threshold", "type", "integer")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("parameters") && d.contains("duplicate name") && d.contains("Threshold"));
                });
    }

    @Test
    void validate_crossCategoryCollision_baseElementVsSubpopulation_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .baseElements(List.of(
                        Map.of("name", "High Risk")
                ))
                .subpopulations(List.of(
                        Map.of("subpopulationName", "High Risk")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("subpopulations") && d.contains("conflicts with a base element") && d.contains("High Risk"));
                });
    }

    @Test
    void validate_reservedSystemName_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .baseElements(List.of(
                        Map.of("name", "MeetsInclusionCriteria")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("baseElements") && d.contains("system-generated") && d.contains("MeetsInclusionCriteria"));
                });
    }

    @Test
    void validate_reservedRecommendationNumbered_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .subpopulations(List.of(
                        Map.of("subpopulationName", "Recommendation 2")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("subpopulations") && d.contains("system-generated") && d.contains("Recommendation 2"));
                });
    }

    @Test
    void validate_specialSubpopulations_shouldBeSkipped() {
        // special: true subpopulations are built-in and should not be checked
        Map<String, Object> specialSp = new HashMap<>();
        specialSp.put("special", true);
        specialSp.put("subpopulationName", "Doesn't Meet Inclusion Criteria");

        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .subpopulations(List.of(specialSp))
                .build();

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_uniqueNamesAcrossCategories_shouldPass() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .baseElements(List.of(
                        Map.of("name", "LDL Level"),
                        Map.of("name", "BP Reading")
                ))
                .subpopulations(List.of(
                        Map.of("subpopulationName", "Elderly Patients")
                ))
                .parameters(List.of(
                        Map.of("name", "Threshold", "type", "integer")
                ))
                .build();

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_parameterReservedName_shouldThrow() {
        ArtifactRequest request = ArtifactRequest.builder()
                .name("Test")
                .parameters(List.of(
                        Map.of("name", "InPopulation", "type", "boolean")
                ))
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("parameters") && d.contains("system-generated") && d.contains("InPopulation"));
                });
    }

    // =====================================================================
    // PAT-137 — Modifier values whitelist (defense-in-depth)
    // =====================================================================

    /** Build a single-element artifact carrying one modifier with the given values. */
    private static ArtifactRequest withModifier(String cqlTemplate, Map<String, Object> values) {
        Map<String, Object> modifier = new HashMap<>();
        modifier.put("id", "test-mod");
        modifier.put("name", "Test Modifier");
        modifier.put("cqlTemplate", cqlTemplate);
        if (values != null) modifier.put("values", values);
        return ArtifactRequest.builder()
                .name("Test")
                .expTreeInclude(Map.of(
                        "childInstances", List.of(
                                Map.of("type", "GenericObservation_vsac", "name", "X",
                                        "modifiers", List.of(modifier))
                        )))
                .build();
    }

    @Test
    void validateModifierValues_validValueComparisonNumber_shouldPass() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("ValueComparisonNumber", Map.of(
                "minOperator", ">=",
                "minValue", "5",
                "maxOperator", "<=",
                "maxValue", "10",
                "unit", "mg/dL"));

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validateModifierValues_invalidComparisonOperator_shouldThrow() {
        // Frontend dropdown only offers <, <=, >, >=, =, !=. A malicious client
        // bypassing the UI could send "); /* injected */" — backend must reject.
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("ValueComparisonNumber", Map.of(
                "minOperator", "); DROP TABLE",
                "minValue", "5"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("minOperator") && d.contains("COMPARISON_OP"));
                });
    }

    @Test
    void validateModifierValues_nonNumericValue_shouldThrow() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("ContainsInteger",
                Map.of("value", "5; injected"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("value") && d.contains("NUMERIC"));
                });
    }

    @Test
    void validateModifierValues_invalidUnit_shouldThrow() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        // Quotes in unit would let the caller break out of the 'unit' string literal
        // in generated CQL.
        ArtifactRequest request = withModifier("ConvertUnits",
                Map.of("unit", "mg/dL'; injected"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("unit") && d.contains("UNIT"));
                });
    }

    @Test
    void validateModifierValues_invalidBooleanComparison_shouldThrow() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("BooleanComparison",
                Map.of("value", "is exactly bogus"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("BOOLEAN_COMPARISON_VALUE"));
                });
    }

    @Test
    void validateModifierValues_invalidQualifierType_shouldThrow() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("Qualifier",
                Map.of("qualifier", "arbitrary"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("qualifier") && d.contains("QUALIFIER_TYPE"));
                });
    }

    @Test
    void validateModifierValues_validDateTime_shouldPass() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("BeforeDateTimePrecise",
                Map.of("value", "2025-01-15T10:30:00Z"));

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validateModifierValues_invalidDateTime_shouldThrow() {
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("BeforeDateTimePrecise",
                Map.of("value", "tomorrow"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("value") && d.contains("DATETIME"));
                });
    }

    @Test
    void validateModifierValues_emptyOptionalField_shouldPass() {
        // Empty value fields are legitimate — the engine just skips them.
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("ValueComparisonNumber",
                Map.of("minOperator", ">=", "minValue", "5",
                        "maxOperator", "", "maxValue", "", "unit", ""));

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validateModifierValues_unknownTemplateOrNoValuesMap_shouldNotFail() {
        // CheckExistence has no values; Foo is unknown — neither should error.
        when(templateService.isValidElementType("GenericObservation_vsac")).thenReturn(true);
        when(modifierService.isValidModifierId(anyString())).thenReturn(true);

        ArtifactRequest request = withModifier("CheckExistence", null);
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();

        ArtifactRequest unknown = withModifier("Foo", Map.of("anything", "goes"));
        assertThatCode(() -> validator.validate(unknown)).doesNotThrowAnyException();
    }
}
