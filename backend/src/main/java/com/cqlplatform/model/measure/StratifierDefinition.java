package com.cqlplatform.model.measure;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StratifierDefinition {
    @Size(max = 200)
    private String stratifierId;

    @Size(max = 500)
    private String criteriaExpression;

    @Size(max = 2000)
    private String description;

    @Size(max = 20)
    private List<String> associations;

    /** PAT-233 — how the criteria expression is read: see {@link #KIND_CRITERIA} / {@link #KIND_VALUE}. */
    @Size(max = 20)
    private String kind;

    /** The expression is boolean; patients bucket into the {@code "true"} / {@code "false"} strata. Default. */
    public static final String KIND_CRITERIA = "criteria";

    /**
     * The expression returns the stratum value itself ({@code Patient.gender.value}, an age band
     * label…); every distinct value is a stratum (QM IG conformance 3.17).
     */
    public static final String KIND_VALUE = "value";

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValueBased() {
        return KIND_VALUE.equals(kind);
    }

    /**
     * PAT-235 — a multi-component stratifier (FHIR {@code Measure.group.stratifier.component[]}):
     * each component has its own define, and a patient's stratum is the combination of the
     * components' values (QM IG conformance 3.17). When present, {@link #criteriaExpression}
     * is unused. A patient with no value for any one component is in no stratum.
     */
    @Size(max = 10)
    private List<Component> components;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasComponents() {
        return components != null && !components.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Component {
        /** Labels the component in reports and the exchange package ({@code stratifier.component.code}). */
        @Size(max = 100)
        private String code;

        @Size(max = 500)
        private String criteriaExpression;

        @Size(max = 500)
        private String description;

        /** {@link #KIND_CRITERIA} (default) or {@link #KIND_VALUE}, as for the stratifier itself. */
        @Size(max = 20)
        private String kind;
    }
}
