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

    // XSS protection is handled by the frontend (React auto-escapes JSX text content).
    // No server-side HTML escaping is needed for CDS card display values.

    @Test
    void formatDetail_preservesRawContent() {
        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.getCode().setText("<script>alert('xss')</script>");

        String result = formatter.formatDetail(obs);

        // Raw content preserved — React handles escaping at render time
        assertThat(result).contains("<script>alert('xss')</script>");
    }
}
