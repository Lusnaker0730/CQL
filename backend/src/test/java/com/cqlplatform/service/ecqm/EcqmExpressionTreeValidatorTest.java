package com.cqlplatform.service.ecqm;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.ecqm.EcqmArtifactRequest;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.authoring.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scopes tests to the aggregateMethod save-time validation added in #PAT-089.
 *
 * <p>The broader structural and XSS validation in {@link EcqmExpressionTreeValidator}
 * is covered indirectly by integration tests; these tests specifically lock the
 * contract that unknown aggregateMethod values are rejected at save time rather
 * than silently accepted (and later yielding null evaluation scores per #PAT-088).
 */
class EcqmExpressionTreeValidatorTest {

    private EcqmExpressionTreeValidator validator;

    @BeforeEach
    void setUp() {
        // Template / modifier services aren't exercised by aggregateMethod tests —
        // mocking them keeps the test focused on the validation piece.
        TemplateService templateService = Mockito.mock(TemplateService.class);
        ModifierService modifierService = Mockito.mock(ModifierService.class);
        // Make validation of node types permissive — our tests only exercise the
        // aggregateMethod path; node-type checks aren't what we're testing here.
        Mockito.when(templateService.isValidElementType(Mockito.anyString())).thenReturn(true);
        // Permissive modifier id check — PAT-139 modifier-values tests use a
        // synthetic id; the structural id check is exercised elsewhere.
        Mockito.when(modifierService.isValidModifierId(Mockito.anyString())).thenReturn(true);
        validator = new EcqmExpressionTreeValidator(templateService, modifierService);
    }

    private EcqmArtifactRequest requestWithObservations(List<Map<String, Object>> observations) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        group.put("populations", new HashMap<>());
        group.put("observations", observations);

        List<Map<String, Object>> groups = new ArrayList<>();
        groups.add(group);

        return EcqmArtifactRequest.builder()
                .name("Test Measure")
                .status("draft")
                .scoringType("continuous-variable")
                .populationGroups(groups)
                .build();
    }

    private Map<String, Object> observation(String aggregateMethod) {
        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("observationId", "obs-1");
        if (aggregateMethod != null) {
            obs.put("aggregateMethod", aggregateMethod);
        }
        return obs;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "count", "Count", "COUNT",
            "sum", "Sum",
            "average", "Average", "avg", "Avg", "mean", "Mean",
            "median", "Median",
            "minimum", "Minimum", "min", "Min", "MIN",
            "maximum", "Maximum", "max", "Max", "MAX"
    })
    void validAggregateMethods_shouldNotThrow(String method) {
        EcqmArtifactRequest request = requestWithObservations(List.of(observation(method)));
        // No exception expected — canonical forms and documented aliases all valid.
        validator.validate(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Minumum",  // typo
            "Maxmum",   // typo
            "avergae",  // typo
            "Percentile", // unsupported (not in backend's switch)
            "StdDev",
            "garbage"
    })
    void unknownAggregateMethods_shouldThrowValidationException(String method) {
        EcqmArtifactRequest request = requestWithObservations(List.of(observation(method)));
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid eCQM artifact data")
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails())
                            .anyMatch(detail -> detail.contains("aggregateMethod")
                                    && detail.contains(method)
                                    && detail.contains("not a recognized"));
                });
    }

    @Test
    void nullAggregateMethod_shouldBeAccepted() {
        // Preserves the "no aggregate specified → average" default semantic from
        // MeasureScoreCalculator. Null / missing is a legitimate state (author chose
        // to not override the default); only NON-NULL TYPOS are errors.
        EcqmArtifactRequest request = requestWithObservations(List.of(observation(null)));
        validator.validate(request);
    }

    @Test
    void blankAggregateMethod_shouldBeAccepted() {
        // Same semantic as null — blank string behaves as "not specified".
        EcqmArtifactRequest request = requestWithObservations(List.of(observation("   ")));
        validator.validate(request);
    }

    @Test
    void missingObservations_shouldBeAccepted() {
        // Many eCQM measures don't have observations at all (proportion, ratio,
        // cohort). Validator must not crash on that.
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        group.put("populations", new HashMap<>());
        // no "observations" key

        EcqmArtifactRequest request = EcqmArtifactRequest.builder()
                .name("Test Measure")
                .status("draft")
                .scoringType("proportion")
                .populationGroups(List.of(group))
                .build();
        validator.validate(request);
    }

    @Test
    void multipleObservationsWithOneBadMethod_shouldIdentifyOffender() {
        // When several observations exist, the validation error points at the
        // specific bad one (path including indices) — so the author knows which
        // one to fix.
        EcqmArtifactRequest request = requestWithObservations(List.of(
                observation("Average"),     // OK
                observation("BadMethod"),   // ← offender at index 1
                observation("Sum")          // OK
        ));
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails())
                            .anyMatch(d -> d.contains("observations[1]") && d.contains("BadMethod"));
                });
    }

    // =====================================================================
    // PAT-139 — Modifier values whitelist (defense-in-depth, shared with authoring)
    // =====================================================================

    /**
     * Build a request with a baseElement carrying a single modifier with the given
     * cqlTemplate + values map. Lets us exercise the same whitelist code path as
     * authoring.ExpressionTreeValidator without copy-pasting the modifier table.
     */
    private static EcqmArtifactRequest requestWithModifier(String cqlTemplate, Map<String, Object> values) {
        Map<String, Object> modifier = new HashMap<>();
        modifier.put("id", "test-mod");
        modifier.put("name", "Test Modifier");
        modifier.put("cqlTemplate", cqlTemplate);
        if (values != null) modifier.put("values", values);

        Map<String, Object> baseElement = new HashMap<>();
        baseElement.put("type", "GenericObservation_vsac");
        baseElement.put("name", "X");
        baseElement.put("modifiers", List.of(modifier));

        return EcqmArtifactRequest.builder()
                .name("Test Measure")
                .status("draft")
                .scoringType("proportion")
                .baseElements(List.of(baseElement))
                .build();
    }

    @Test
    void modifierValues_invalidComparisonOperator_shouldThrow() {
        EcqmArtifactRequest request = requestWithModifier("ValueComparisonNumber", Map.of(
                "minOperator", "); DROP TABLE",
                "minValue", "5"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("minOperator") && d.contains("COMPARISON_OP"));
                });
    }

    @Test
    void modifierValues_invalidUnit_shouldThrow() {
        // Quote in unit would let the caller break out of the 'unit' literal in CQL.
        EcqmArtifactRequest request = requestWithModifier("ConvertUnits",
                Map.of("unit", "mg/dL'; injected"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("unit") && d.contains("UNIT"));
                });
    }

    @Test
    void modifierValues_invalidDateTime_shouldThrow() {
        EcqmArtifactRequest request = requestWithModifier("BeforeDateTimePrecise",
                Map.of("value", "tomorrow"));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d ->
                            d.contains("value") && d.contains("DATETIME"));
                });
    }

    @Test
    void modifierValues_validCanonicalValues_shouldPass() {
        EcqmArtifactRequest request = requestWithModifier("ValueComparisonNumber", Map.of(
                "minOperator", ">=",
                "minValue", "5",
                "maxOperator", "<=",
                "maxValue", "10",
                "unit", "mg/dL"));
        validator.validate(request); // no exception
    }

    @Test
    void modifierValues_emptyOptionalFields_shouldPass() {
        // Empty optional fields are legitimate (engine just skips them).
        EcqmArtifactRequest request = requestWithModifier("ValueComparisonNumber",
                Map.of("minOperator", ">=", "minValue", "5",
                        "maxOperator", "", "maxValue", "", "unit", ""));
        validator.validate(request);
    }

    @Test
    void modifierValues_unknownTemplateOrNoValues_shouldNotFail() {
        // CheckExistence has no value fields; "Foo" is unknown — neither errors.
        validator.validate(requestWithModifier("CheckExistence", null));
        validator.validate(requestWithModifier("Foo", Map.of("anything", "goes")));
    }
}
