package com.cqlplatform.service.cds;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class CdsResourceFormatterTest {

    private final CdsResourceFormatter formatter = new CdsResourceFormatter();

    @Test
    void formatDetail_observation_withCodeAndValue() {
        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.getCode().setText("Blood Pressure");
        obs.setValue(new Quantity().setValue(new BigDecimal("120")).setUnit("mmHg"));

        String result = formatter.formatDetail(obs);

        assertThat(result).contains("**Observation**");
        assertThat(result).contains("obs-1");
        assertThat(result).contains("Code: Blood Pressure");
        assertThat(result).contains("Value: 120 mmHg");
    }

    @Test
    void formatDetail_condition_withCodeAndStatus() {
        Condition cond = new Condition();
        cond.setId("cond-1");
        cond.getCode().setText("Diabetes");
        cond.getClinicalStatus().addCoding().setCode("active");

        String result = formatter.formatDetail(cond);

        assertThat(result).contains("**Condition**");
        assertThat(result).contains("Condition: Diabetes");
        assertThat(result).contains("Status: active");
    }

    @Test
    void formatDetail_medicationRequest() {
        MedicationRequest medReq = new MedicationRequest();
        medReq.setId("med-1");
        medReq.setMedication(new CodeableConcept().setText("Aspirin"));
        medReq.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);

        String result = formatter.formatDetail(medReq);

        assertThat(result).contains("**MedicationRequest**");
        assertThat(result).contains("Medication: Aspirin");
        assertThat(result).contains("Status: active");
    }

    @Test
    void formatDetail_procedure() {
        Procedure proc = new Procedure();
        proc.setId("proc-1");
        proc.getCode().setText("Appendectomy");
        proc.setStatus(Procedure.ProcedureStatus.COMPLETED);

        String result = formatter.formatDetail(proc);

        assertThat(result).contains("**Procedure**");
        assertThat(result).contains("Procedure: Appendectomy");
        assertThat(result).contains("Status: completed");
    }

    @Test
    void formatDetail_allergyIntolerance() {
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setId("allergy-1");
        allergy.getCode().setText("Penicillin");
        allergy.getClinicalStatus().addCoding().setCode("active");

        String result = formatter.formatDetail(allergy);

        assertThat(result).contains("**AllergyIntolerance**");
        assertThat(result).contains("Allergy: Penicillin");
        assertThat(result).contains("Status: active");
    }

    @Test
    void formatDetail_unknownResource() {
        Patient patient = new Patient();
        patient.setId("p-1");

        String result = formatter.formatDetail(patient);

        assertThat(result).contains("**Patient**");
        assertThat(result).contains("p-1");
    }

    @Test
    void formatReference_withId() {
        Observation obs = new Observation();
        obs.setId("obs-1");

        String result = formatter.formatReference(obs);
        assertThat(result).isEqualTo("Observation/obs-1");
    }

    @Test
    void formatReference_withoutId() {
        Observation obs = new Observation();

        String result = formatter.formatReference(obs);
        assertThat(result).isEqualTo("Observation");
    }

    // --- XSS escape verification tests ---

    @Test
    void formatDetail_shouldEscapeXssInCodeText() {
        Observation obs = new Observation();
        obs.setId("obs-xss");
        obs.getCode().setText("<script>alert('xss')</script>");

        String result = formatter.formatDetail(obs);

        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void formatDetail_shouldEscapeXssInCodingDisplay() {
        Observation obs = new Observation();
        obs.setId("obs-xss2");
        obs.getCode().addCoding().setDisplay("<svg onload=alert(1)>");

        String result = formatter.formatDetail(obs);

        assertThat(result).doesNotContain("<svg");
        assertThat(result).contains("&lt;svg");
    }

    @Test
    void formatDetail_shouldEscapeXssInQuantityUnit() {
        Observation obs = new Observation();
        obs.setId("obs-xss3");
        obs.getCode().setText("BP");
        obs.setValue(new Quantity().setValue(new BigDecimal("120")).setUnit("<img src=x onerror=alert(1)>"));

        String result = formatter.formatDetail(obs);

        assertThat(result).doesNotContain("<img");
        assertThat(result).contains("&lt;img");
    }

    @Test
    void formatDetail_shouldEscapeXssInId() {
        Observation obs = new Observation();
        obs.setId("<script>alert(1)</script>");

        String result = formatter.formatDetail(obs);

        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void formatReference_shouldEscapeXssInId() {
        Observation obs = new Observation();
        obs.setId("<img onerror=alert(1)>");

        String result = formatter.formatReference(obs);

        assertThat(result).doesNotContain("<img");
        assertThat(result).contains("&lt;img");
    }

    @Test
    void formatDetail_shouldEscapeXssInConditionStatus() {
        Condition cond = new Condition();
        cond.setId("cond-xss");
        cond.getClinicalStatus().addCoding().setCode("<script>xss</script>");

        String result = formatter.formatDetail(cond);

        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }
}
