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
}
