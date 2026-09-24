package com.cqlplatform.service.measure;

import com.cqlplatform.exception.ValidationException;

import java.time.LocalDate;

/**
 * PAT-236 — rules for the standard FHIR Measure metadata, shared by the measure and the eCQM
 * artifact services so both surfaces reject the same input.
 */
public final class MeasureMetadataRules {

    private MeasureMetadataRules() {
    }

    /** Measure.effectivePeriod is a Period: when both ends are given the end must not precede the start. */
    public static void requireOrderedEffectivePeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("Effective period ends (" + end + ") before it starts (" + start + ")");
        }
    }
}
