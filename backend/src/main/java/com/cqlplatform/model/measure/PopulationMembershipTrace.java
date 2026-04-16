package com.cqlplatform.model.measure;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Per-patient trace of how CQL expression results map to each population's
 * membership after applying the scoring-type hierarchy (IP → Denom → Numer, etc.).
 *
 * <p>Answers the question: "Why did this patient (not) end up in the Numerator?"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PopulationMembershipTrace {

    private List<GroupTrace> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupTrace {
        private String groupId;
        private String description;
        /** "proportion" | "ratio" | "continuous-variable" | "cohort" */
        private String scoringType;
        private List<PopulationTraceEntry> populations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PopulationTraceEntry {
        /** Canonical population identifier (e.g. "Initial Population" or FHIR code). */
        private String populationType;
        /** Human-readable label for non-UI consumers. */
        private String displayName;
        /** CQL expression name bound to this population. */
        private String criteriaExpression;
        /** Raw boolean from CQL before hierarchy gating. */
        private Boolean rawResult;
        /** After hierarchy gating / exclusion application. */
        private Boolean effectiveResult;
        /** i18n key describing the reason (e.g. "denom_excludedOut"). */
        private String reasonCode;
        /** Template variables for the reason message (e.g. {ip: true}). */
        private Map<String, Boolean> reasonInputs;
    }
}
