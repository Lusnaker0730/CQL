package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationResult.SupplementalDataResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.ValueCount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PAT-234 — supplemental data elements and risk adjustment factors as value distributions.
 *
 * <p>A measure declares them by define name ({@link MeasureDefinition#getSupplementalData()},
 * {@link MeasureDefinition#getRiskAdjustments()}); for every evaluated patient the define's
 * value is bucketed by the same key rule strata use ({@link ValueKeys}), so {@code SDE Sex}
 * comes out as {@code female: 12, male: 9} rather than a meaningless count of 21. Only the
 * declared defines are collected — helper defines and stratifiers are not supplemental data.
 *
 * <p>What this is not: a risk model. The QM IG (3.4.9) carries risk adjustment as data
 * ({@code supplementalData} with usage {@code risk-adjustment-factor}); computing an adjusted
 * rate needs coefficients the measure does not carry.
 */
@Component
public class SupplementalDataEvaluator {

    /** A declared element: what to read from the results and how to label it. */
    public record Declared(String definition, String usage, String description) {
    }

    /** Running distribution of one element's values across patients. */
    public static final class Distribution {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        int withoutValue;
    }

    /** The measure's SDEs (usage {@code supplemental-data}) then its RAFs (usage {@code risk-adjustment-factor}). */
    public List<Declared> declared(MeasureDefinition definition) {
        List<Declared> out = new ArrayList<>();
        if (definition == null) return out;
        if (definition.getSupplementalData() != null) {
            for (MeasureDefinition.SupplementalDataDef sde : definition.getSupplementalData()) {
                if (sde.getDefinition() != null && !sde.getDefinition().isBlank()) {
                    out.add(new Declared(sde.getDefinition(), CqfmConstants.USAGE_SUPPLEMENTAL_DATA, sde.getDescription()));
                }
            }
        }
        if (definition.getRiskAdjustments() != null) {
            for (MeasureDefinition.RiskAdjustmentDef raf : definition.getRiskAdjustments()) {
                if (raf.getDefinition() != null && !raf.getDefinition().isBlank()) {
                    out.add(new Declared(raf.getDefinition(), CqfmConstants.USAGE_RISK_ADJUSTMENT_FACTOR, raf.getDescription()));
                }
            }
        }
        return out;
    }

    /**
     * Bucket one patient's values. A define missing from the results (not evaluated) or
     * evaluating to nothing counts as "without value".
     *
     * @param data accumulated: definition → distribution (insertion-ordered by first value seen)
     */
    public void accumulate(List<Declared> declared, Map<String, CqlExecutionResponse.ExpressionResult> results,
                           Map<String, Distribution> data) {
        for (Declared element : declared) {
            Distribution distribution = data.computeIfAbsent(element.definition(), k -> new Distribution());
            CqlExecutionResponse.ExpressionResult result = results == null ? null : results.get(element.definition());
            String key = result == null ? null : ValueKeys.of(result.getValue());
            if (key == null) {
                distribution.withoutValue++;
            } else {
                distribution.counts.merge(key, 1, Integer::sum);
            }
        }
    }

    /** The report rows, one per declared element, in declaration order (SDEs first, then RAFs). */
    public List<SupplementalDataResult> build(List<Declared> declared, Map<String, Distribution> data) {
        List<SupplementalDataResult> out = new ArrayList<>();
        for (Declared element : declared) {
            Distribution distribution = data.getOrDefault(element.definition(), new Distribution());
            List<ValueCount> values = new ArrayList<>();
            for (Map.Entry<String, Integer> e : distribution.counts.entrySet()) {
                values.add(ValueCount.builder().value(e.getKey()).count(e.getValue()).build());
            }
            out.add(SupplementalDataResult.builder()
                    .definition(element.definition())
                    .usage(element.usage())
                    .description(element.description())
                    .patientsWithoutValue(distribution.withoutValue)
                    .values(values)
                    .build());
        }
        return out;
    }
}
