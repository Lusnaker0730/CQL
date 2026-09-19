package com.cqlplatform.model.measure;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured expected (or actual) values of one test case, per population group (PAT-228).
 *
 * <p>Replaces the flat {@code Map<String, Boolean>} for measures it cannot describe:
 * multi-group measures (both groups have a "numerator"), measure observations
 * (continuous-variable / ratio) and stratifiers. The same shape is used for the
 * expectation a user enters and for the actual values a run produces, so the two can be
 * shown side by side.
 *
 * <p>Population values are <em>effective</em> counts — after the population hierarchy of
 * the scoring type has been applied (a patient removed by a denominator exclusion has
 * numerator 0 even if the raw "Numerator" define is true). They are computed by the same
 * code the production evaluation uses, so "this test passes" means "production counts this
 * patient the same way". Counts are integers: the evaluator is patient-based today (0 / 1),
 * and the format stays valid once episode-level counting exists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestCaseExpectedValues {

    @Valid
    @Size(max = 50)
    private List<GroupValues> groups;

    /**
     * True when there is nothing structured to compare — callers fall back to the legacy map.
     * {@code @JsonIgnore}: a bean-style {@code isEmpty()} would otherwise be written as an
     * {@code "empty"} property that cannot be read back.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return groups == null || groups.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupValues {

        /** {@link GroupDefinition#getGroupId()} of the group these values belong to. */
        @Size(max = 100)
        private String groupId;

        /**
         * Effective count per population, keyed by the group's own
         * {@link PopulationDefinition#getPopulationType()} (e.g. {@code "initial-population"}).
         * A population that is not listed is expected to be 0.
         */
        @Size(max = 50)
        private Map<String, Integer> populations;

        /**
         * Measure observation values this patient contributes (order-insensitive).
         * {@code null} = observations are not asserted; an empty list = none expected.
         */
        @Size(max = 500)
        private List<Double> observations;

        /**
         * Expected stratum per stratifier: stratifierId → value as the evaluator renders it
         * ({@code "true"} / {@code "false"} for criteria stratifiers). Only the listed
         * stratifiers are asserted. An empty string means "falls into no stratum".
         */
        @Size(max = 50)
        private Map<String, String> stratifiers;
    }
}
