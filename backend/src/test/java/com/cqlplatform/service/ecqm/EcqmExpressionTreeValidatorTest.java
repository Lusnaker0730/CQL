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
        Mockito.when(modifierService.getById(Mockito.anyString())).thenReturn(null);
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
}
