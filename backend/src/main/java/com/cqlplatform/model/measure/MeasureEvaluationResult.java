package com.cqlplatform.model.measure;

import com.cqlplatform.model.debug.ExecutionErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Serialized form of a measure evaluation's result.
 *
 * <p>{@code @NoArgsConstructor + @AllArgsConstructor + @Builder} is required for Jackson:
 * without an explicit no-arg constructor, Lombok's {@code @Builder} generates an
 * all-args constructor that Jackson cannot use by default, and deserialization silently
 * returns {@code null} (historical bug tracked by PAT-075 / code review issue #6 —
 * previously masked by a silent catch block in MeasureReportEntity.@PostLoad).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureEvaluationResult {
    private String measureId;
    private String measureName;
    private String status;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String reportType;
    private List<GroupResult> groups;
    private Map<String, Object> supplementalData;

    /**
     * PAT-234 — the measure's declared supplemental data elements and risk adjustment factors,
     * each as a distribution of values over the evaluated patients (value → patient count).
     * Unlike {@link #supplementalData} (a legacy define → count map that drops strings), this
     * keeps every value: it is what a risk model or a cross-site comparison needs.
     */
    private List<SupplementalDataResult> supplementalDataResults;
    private String errorMessage;

    /**
     * Structured error classification populated on evaluation failure. Complements
     * the free-form {@link #errorMessage} so measure authors can see whether the
     * failure came from CQL translation, runtime execution, or population
     * evaluation without parsing strings. Null on successful evaluations.
     */
    private ExecutionErrorInfo errorInfo;

    /**
     * Number of patients whose CQL evaluation failed during this run (PAT-140).
     * A non-zero value with status=COMPLETE means the score was computed from a
     * partial cohort — the UI uses this to surface a warning so authors don't
     * silently report a denominator that lost members to runtime errors.
     */
    private Integer errorCount;

    /**
     * Total patients submitted for evaluation in this run (denominator before
     * filtering, before population logic). Pairs with {@link #errorCount} so the
     * caller can compute the failure ratio without re-fetching the patient list.
     */
    private Integer evaluatedPatientCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupResult {
        private String groupId;
        private String description;
        private List<PopulationResult> populations;
        private Double measureScore;
        private String measureScoreUnit;
        private List<StratifierResult> stratifiers;
        private Integer totalPatients;
        private ObservationStatistics observationStatistics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObservationStatistics {
        private String aggregateMethod;
        private Double aggregateValue;
        private Integer observationCount;
        private Double minimum;
        private Double maximum;
        private Double average;
        private Double median;
        private String unit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopulationResult {
        private String populationType; // initial-population, numerator, denominator, etc.
        private String populationId;
        private Integer count;
        private List<String> subjectIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StratifierResult {
        private String strataId;
        private String strataValue;
        private List<PopulationResult> populations;
        private Double measureScore;
    }

    /** PAT-234 — one supplemental data element / risk adjustment factor over all evaluated patients. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementalDataResult {
        /** The CQL define name. */
        private String definition;
        /** {@code supplemental-data} or {@code risk-adjustment-factor} (FHIR measure-data-usage). */
        private String usage;
        private String description;
        /** Patients whose value was null / empty — they are in no bucket. */
        private Integer patientsWithoutValue;
        /** Distinct values in first-seen order, each with the number of patients that had it. */
        private List<ValueCount> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueCount {
        private String value;
        private Integer count;
    }
}
