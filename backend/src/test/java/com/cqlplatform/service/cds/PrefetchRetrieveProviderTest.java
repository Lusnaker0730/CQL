package com.cqlplatform.service.cds;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.opencds.cqf.cql.engine.runtime.Code;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PrefetchRetrieveProviderTest {

    @Test
    void retrieve_byDataType_shouldReturnMatchingResources() {
        Patient patient = new Patient();
        patient.setId("p1");
        Observation obs = new Observation();
        obs.setId("obs1");

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(patient, obs), "p1");

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Observation",
                null, null, null, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).hasSize(1);
    }

    @Test
    void retrieve_nonExistentType_shouldReturnEmpty() {
        Patient patient = new Patient();
        patient.setId("p1");

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(patient), "p1");

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Condition",
                null, null, null, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).isEmpty();
    }

    @Test
    void retrieve_withCodeFilter_shouldFilterByCode() {
        Observation obs1 = new Observation();
        obs1.setCode(new CodeableConcept().addCoding(new Coding().setCode("12345")));
        Observation obs2 = new Observation();
        obs2.setCode(new CodeableConcept().addCoding(new Coding().setCode("99999")));

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(obs1, obs2), "p1");

        List<Code> codes = List.of(new Code().withCode("12345"));

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Observation",
                null, "code", codes, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).hasSize(1);
    }

    @Test
    void retrieve_conditionWithCodeFilter_shouldFilter() {
        Condition cond = new Condition();
        cond.setCode(new CodeableConcept().addCoding(new Coding().setCode("E11")));

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(cond), "p1");

        List<Code> codes = List.of(new Code().withCode("E11"));

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Condition",
                null, "code", codes, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).hasSize(1);
    }

    @Test
    void retrieve_medicationRequestWithCodeFilter_shouldFilter() {
        MedicationRequest medReq = new MedicationRequest();
        medReq.setMedication(new CodeableConcept().addCoding(new Coding().setCode("metformin")));

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(medReq), "p1");

        List<Code> codes = List.of(new Code().withCode("metformin"));

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "MedicationRequest",
                null, "medication", codes, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).hasSize(1);
    }

    @Test
    void retrieve_emptyResourceList_shouldReturnEmpty() {
        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(), "p1");

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Observation",
                null, null, null, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).isEmpty();
    }

    @Test
    void retrieve_withoutCodePath_shouldReturnAllOfType() {
        Observation obs1 = new Observation();
        obs1.setId("obs1");
        Observation obs2 = new Observation();
        obs2.setId("obs2");

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(obs1, obs2), "p1");

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Observation",
                null, null, null, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).hasSize(2);
    }

    @Test
    void retrieve_multipleResourceTypes_shouldOrganizeCorrectly() {
        Patient patient = new Patient();
        Observation obs = new Observation();
        Condition cond = new Condition();

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(patient, obs, cond), "p1");

        List<Object> patients = new ArrayList<>();
        provider.retrieve("Patient", null, null, "Patient",
                null, null, null, null, null, null, null, null).forEach(patients::add);

        List<Object> observations = new ArrayList<>();
        provider.retrieve("Patient", null, null, "Observation",
                null, null, null, null, null, null, null, null).forEach(observations::add);

        assertThat(patients).hasSize(1);
        assertThat(observations).hasSize(1);
    }

    @Test
    void retrieve_codeFilterNoMatch_shouldReturnEmpty() {
        Observation obs = new Observation();
        obs.setCode(new CodeableConcept().addCoding(new Coding().setCode("AAA")));

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(
                List.of(obs), "p1");

        List<Code> codes = List.of(new Code().withCode("ZZZ"));

        Iterable<Object> results = provider.retrieve(
                "Patient", null, null, "Observation",
                null, "code", codes, null, null, null, null, null);

        List<Object> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        assertThat(resultList).isEmpty();
    }
}
