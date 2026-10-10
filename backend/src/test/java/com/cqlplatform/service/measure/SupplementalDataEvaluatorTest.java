package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationResult.SupplementalDataResult;
import com.cqlplatform.service.measure.SupplementalDataEvaluator.Declared;
import com.cqlplatform.service.measure.SupplementalDataEvaluator.Distribution;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-234 — supplemental data and risk adjustment factors as value distributions. Before,
 * every non-population define was reduced to a count (booleans +1, lists by size) and strings
 * were dropped — {@code SDE Sex} reported nothing at all.
 */
class SupplementalDataEvaluatorTest {

    private final SupplementalDataEvaluator evaluator = new SupplementalDataEvaluator();

    private static CqlExecutionResponse.ExpressionResult value(Object v) {
        return CqlExecutionResponse.ExpressionResult.builder().value(v).build();
    }

    private static MeasureDefinition measure() {
        return MeasureDefinition.builder()
                .supplementalData(List.of(
                        MeasureDefinition.SupplementalDataDef.builder().definition("SDE Sex").description("Sex").build(),
                        MeasureDefinition.SupplementalDataDef.builder().definition("  ").build()))
                .riskAdjustments(List.of(
                        MeasureDefinition.RiskAdjustmentDef.builder().definition("RAF Age Band").build(),
                        MeasureDefinition.RiskAdjustmentDef.builder().definition("RAF Diabetes").build()))
                .build();
    }

    @Test
    void declared_listsSdesThenRafs_withTheirUsage_skippingBlanks() {
        List<Declared> declared = evaluator.declared(measure());
        assertThat(declared).extracting(d -> d.definition() + "|" + d.usage() + "|" + d.description())
                .containsExactly("SDE Sex|supplemental-data|Sex",
                        "RAF Age Band|risk-adjustment-factor|null",
                        "RAF Diabetes|risk-adjustment-factor|null");
        assertThat(evaluator.declared(null)).isEmpty();
        assertThat(evaluator.declared(MeasureDefinition.builder().build())).isEmpty();
    }

    @Test
    void accumulate_bucketsEachPatientByValue_countingMissingSeparately_inFirstSeenOrder() {
        List<Declared> declared = evaluator.declared(measure());
        Map<String, Distribution> data = new LinkedHashMap<>();

        Object[][] patients = {
                // sex, age band, diabetes
                {"female", "65+", true},
                {"male", "18-49", false},
                {"female", null, true},
                {null, "65+", null},
                {"", "  ", "null"},          // blank / "null" text: no value, like a stratum
        };
        for (Object[] p : patients) {
            Map<String, CqlExecutionResponse.ExpressionResult> results = new HashMap<>();
            results.put("SDE Sex", value(p[0]));
            results.put("RAF Age Band", value(p[1]));
            results.put("RAF Diabetes", value(p[2]));
            results.put("Initial Population", value(true)); // not declared → ignored
            evaluator.accumulate(declared, results, data);
        }
        // a patient whose results lack the define entirely
        evaluator.accumulate(declared, Map.of(), data);

        List<SupplementalDataResult> out = evaluator.build(declared, data);
        assertThat(out).extracting(SupplementalDataResult::getDefinition).containsExactly("SDE Sex", "RAF Age Band", "RAF Diabetes");

        SupplementalDataResult sex = out.get(0);
        assertThat(sex.getValues()).extracting(v -> v.getValue() + "=" + v.getCount()).containsExactly("female=2", "male=1");
        assertThat(sex.getPatientsWithoutValue()).isEqualTo(3);
        assertThat(sex.getUsage()).isEqualTo("supplemental-data");

        SupplementalDataResult band = out.get(1);
        assertThat(band.getValues()).extracting(v -> v.getValue() + "=" + v.getCount()).containsExactly("65+=2", "18-49=1");
        assertThat(band.getPatientsWithoutValue()).isEqualTo(3);
        assertThat(band.getUsage()).isEqualTo("risk-adjustment-factor");

        SupplementalDataResult diabetes = out.get(2);
        assertThat(diabetes.getValues()).extracting(v -> v.getValue() + "=" + v.getCount()).containsExactly("true=2", "false=1");
        assertThat(diabetes.getPatientsWithoutValue()).isEqualTo(3);
    }

    @Test
    void build_listsADeclaredElementNobodyHadAValueFor_andUsesTheSharedKeyRule() {
        List<Declared> declared = List.of(new Declared("RAF Payer", "risk-adjustment-factor", null));
        Map<String, Distribution> data = new LinkedHashMap<>();
        assertThat(evaluator.build(declared, data)).singleElement()
                .satisfies(e -> {
                    assertThat(e.getValues()).isEmpty();
                    assertThat(e.getPatientsWithoutValue()).isZero();
                });

        Map<String, Object> code = new LinkedHashMap<>();
        code.put("code", "NHI");
        code.put("display", "National Health Insurance");
        evaluator.accumulate(declared, Map.of("RAF Payer", value(code)), data);
        evaluator.accumulate(declared, Map.of("RAF Payer", value(List.of("A", "B"))), data);
        assertThat(evaluator.build(declared, data).get(0).getValues())
                .extracting(v -> v.getValue()).containsExactly("NHI (National Health Insurance)", "A, B");
    }
}
