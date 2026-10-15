package com.cqlplatform.service.cql;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import com.cqlplatform.service.measure.MeasureScoreCalculator;
import com.cqlplatform.service.measure.PopulationEvaluator;
import com.cqlplatform.testsupport.Fuzz;
import org.hl7.fhir.r4.model.Age;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * PAT-265 — fuzzing the production execution path with random patient data.
 *
 * <p>A measure-like library is translated once ({@code translateOnce}, as
 * {@code MeasureEvaluationService} does) and then executed for many randomly generated patients
 * through {@code executeWithPreTranslated} + {@link PrefetchRetrieveProvider} — the same objects a
 * real {@code $evaluate-measure} uses after its bulk fetch. The generator deliberately produces the
 * shapes real EHR data has and hand-written fixtures never do: codings with a wrong or missing
 * system, empty codings, text-only concepts, partial-precision dates, one-sided periods, every
 * {@code value[x]} / {@code effective[x]} / {@code onset[x]} / {@code medication[x]} choice,
 * quantities without units, statuses missing. Because the generator knows what it planted, the
 * expected answers (an oracle) are computed in plain Java and compared with the engine.
 *
 * <p>The invariants lock the PAT-231 class of failure — a value model change that makes every
 * population silently empty — as well as the population algebra and observation extraction that
 * sit between the engine and a report.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PAT-265 — CQL engine fuzz: random patients through the production execution path")
class CqlEngineFuzzTest {

    private static final String ICD10 = "http://hl7.org/fhir/sid/icd-10-cm";
    private static final String SNOMED = "http://snomed.info/sct";
    private static final String LOINC = "http://loinc.org";
    private static final String ACT_CODE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String ATC = "http://www.whocc.no/atc";
    private static final String HBA1C = "4548-4";

    private static final String CQL = """
            library FuzzMeasure version '1.0.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1' called FHIRHelpers

            codesystem "LOINC": 'http://loinc.org'
            code "HbA1c": '4548-4' from "LOINC" display 'Hemoglobin A1c'

            parameter "Measurement Period" Interval<DateTime>
              default Interval[@2025-01-01T00:00:00.0, @2025-12-31T23:59:59.999]

            context Patient

            define "Diabetes Conditions":
              [Condition] C
                where exists (C.code.coding X
                  where X.system = 'http://hl7.org/fhir/sid/icd-10-cm'
                    and (StartsWith(X.code.value, 'E08') or StartsWith(X.code.value, 'E09')
                      or StartsWith(X.code.value, 'E10') or StartsWith(X.code.value, 'E11')
                      or StartsWith(X.code.value, 'E12') or StartsWith(X.code.value, 'E13')))

            define "Has Diabetes": exists "Diabetes Conditions"

            define "Final HbA1c Tests": [Observation: "HbA1c"] O where O.status = 'final'

            define "Has HbA1c Test": exists "Final HbA1c Tests"

            define "HbA1c Values":
              "Final HbA1c Tests" O
                where O.value is FHIR.Quantity and (O.value as FHIR.Quantity).value is not null
                return (O.value as FHIR.Quantity).value

            define "Finished Encounters": [Encounter] E where E.status = 'finished'

            define "Encounters In Period": [Encounter] E where E.period overlaps "Measurement Period"

            define "Encounter Count": Count([Encounter])
            define "Condition Count": Count([Condition])
            define "Observation Count": Count([Observation])
            define "Medication Count": Count([MedicationRequest])
            define "Patient Gender": Patient.gender

            define "Initial Population": "Has Diabetes"
            define "Denominator": "Initial Population"
            define "Numerator": "Has Diabetes" and "Has HbA1c Test"
            """;

    private static final GroupDefinition PATIENT_GROUP = GroupDefinition.builder()
            .groupId("patients")
            .populations(List.of(
                    population("initial-population", "Initial Population"),
                    population("denominator", "Denominator"),
                    population("numerator", "Numerator")))
            .build();

    /** An episode-based group: counts finished encounters, a Boolean numerator keeps all or none of them. */
    private static final GroupDefinition EPISODE_GROUP = GroupDefinition.builder()
            .groupId("episodes")
            .populationBasis("Encounter")
            .populations(List.of(
                    population("initial-population", "Finished Encounters"),
                    population("denominator", "Finished Encounters"),
                    population("numerator", "Has Diabetes")))
            .build();

    private CqlExecutionService executionService;
    private CqlExecutionService.PreTranslatedContext preTranslated;
    private final PopulationEvaluator populationEvaluator = new PopulationEvaluator();
    private final MeasureScoreCalculator scoreCalculator = new MeasureScoreCalculator();

    @BeforeAll
    void translateOnce() {
        executionService = new CqlExecutionService(
                new FhirDataProviderService(null, null, null),
                new NoOpTerminologyService(),
                Executors.newSingleThreadExecutor(),
                Mockito.mock(CqlLibraryRepository.class));
        setField(executionService, "timeoutSeconds", 30);
        setField(executionService, "maxRetrieveCount", 10000);
        setField(executionService, "maxCollectionSize", 1000);
        setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");
        preTranslated = executionService.translateOnce(CQL);
    }

    // ───────────────────────────────────────────────────────────── the properties

    @Test
    @DisplayName("every random patient executes cleanly, every population define is a Java Boolean, and the engine agrees with the oracle")
    void randomPatients_executeCleanly_andAgreeWithTheOracle() {
        Fuzz.forAll(20261015L, 80, CqlEngineFuzzTest::generate, (patient, random) -> {
            CqlExecutionResponse response = execute(patient);
            assertThat(response.isSuccess()).as("execution success").isTrue();
            assertThat(response.getErrors() == null ? List.of() : response.getErrors()).as("runtime errors").isEmpty();
            Map<String, ExpressionResult> results = response.getResults();
            Oracle oracle = patient.oracle();

            // PAT-231: a Boolean define reaches the platform as java.lang.Boolean — never null, never an engine type
            for (String name : List.of("Initial Population", "Denominator", "Numerator", "Has Diabetes", "Has HbA1c Test")) {
                assertThat(results).as("define present: " + name).containsKey(name);
                assertThat(results.get(name).getValue()).as(name + " is a java.lang.Boolean").isInstanceOf(Boolean.class);
            }
            assertThat(results.get("Has Diabetes").getValue()).as("Has Diabetes").isEqualTo(oracle.hasDiabetes());
            assertThat(results.get("Has HbA1c Test").getValue()).as("Has HbA1c Test").isEqualTo(oracle.hasFinalHbA1c());
            assertThat(results.get("Numerator").getValue()).as("Numerator").isEqualTo(oracle.hasDiabetes() && oracle.hasFinalHbA1c());

            // retrieves return exactly what was planted, whatever shape the resources have
            assertThat(results.get("Condition Count").getValue()).as("Condition Count").isEqualTo(oracle.conditions());
            assertThat(results.get("Observation Count").getValue()).as("Observation Count").isEqualTo(oracle.observations());
            assertThat(results.get("Encounter Count").getValue()).as("Encounter Count").isEqualTo(oracle.encounters());
            assertThat(results.get("Medication Count").getValue()).as("Medication Count").isEqualTo(oracle.medicationRequests());

            // PAT-233: a FHIR primitive serialises as its value (or null when absent)
            assertThat(results.get("Patient Gender").getValue()).as("Patient Gender").isEqualTo(oracle.gender());

            // the population algebra the report is built from (patient basis)
            int ip = oracle.hasDiabetes() ? 1 : 0;
            int numerator = oracle.hasDiabetes() && oracle.hasFinalHbA1c() ? 1 : 0;
            PopulationEvaluator.PatientContribution patientBased =
                    populationEvaluator.contributeToGroup("proportion", List.of(PATIENT_GROUP), PATIENT_GROUP, results);
            assertThat(patientBased.counts()).as("patient-based counts")
                    .containsEntry("Initial Population", ip)
                    .containsEntry("Denominator", ip)
                    .containsEntry("Numerator", numerator);
            assertThat(patientBased.fellBackToPatient()).isFalse();

            // and on an episode basis (PAT-243): the resource list is the population, a Boolean child keeps all or none
            PopulationEvaluator.PatientContribution episodeBased =
                    populationEvaluator.contributeToGroup("proportion", List.of(EPISODE_GROUP), EPISODE_GROUP, results);
            assertThat(episodeBased.counts()).as("episode-based counts")
                    .containsEntry("Initial Population", oracle.finishedEncounters())
                    .containsEntry("Denominator", oracle.finishedEncounters())
                    .containsEntry("Numerator", oracle.hasDiabetes() ? oracle.finishedEncounters() : 0);
            assertThat(episodeBased.fellBackToPatient()).as("an encounter list never falls back to patient counting").isFalse();

            // continuous-variable observation extraction and aggregation
            List<Double> values = populationEvaluator.extractObservationValues(results, "HbA1c Values");
            assertThat(sorted(values)).as("HbA1c observation values").isEqualTo(sorted(oracle.hbA1cValues()));
            Double average = scoreCalculator.calculateContinuousVariableScore(values, "average");
            if (values.isEmpty()) {
                assertThat(average).as("average of no observations").isNull();
            } else {
                double mean = oracle.hbA1cValues().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                assertThat(average).as("average").isCloseTo(mean, within(1e-9));
            }

            // a date-dependent define: no exception, a list of encounter references, never more than the encounters
            Object inPeriod = results.get("Encounters In Period").getValue();
            assertThat(inPeriod).as("Encounters In Period is a list").isInstanceOf(List.class);
            for (Object item : (List<?>) inPeriod) {
                assertThat(String.valueOf(item)).as("episode reference").matches("FHIR\\.Encounter/e-\\d+");
            }
            assertThat(((List<?>) inPeriod).size()).isLessThanOrEqualTo(oracle.encounters());
        });
    }

    @Test
    @DisplayName("executing the same patient twice over the shared pre-translated context gives identical results")
    void sharedPreTranslatedContext_isStateless() {
        Fuzz.forAll(20261016L, 30, CqlEngineFuzzTest::generate, (patient, random) -> {
            Map<String, Object> first = values(execute(patient));
            Map<String, Object> second = values(execute(patient));
            assertThat(second).isEqualTo(first);
        });
    }

    // ───────────────────────────────────────────────────────────── execution

    private CqlExecutionResponse execute(Generated patient) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(CQL);
        request.setPatientId(patient.patientId());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("Measurement Period", new Interval(
                new DateTime(OffsetDateTime.of(LocalDate.of(2025, 1, 1), LocalTime.MIN, ZoneOffset.UTC)), true,
                new DateTime(OffsetDateTime.of(LocalDate.of(2025, 12, 31), LocalTime.MAX, ZoneOffset.UTC)), true));
        request.setParameters(parameters);
        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(patient.resources(), patient.patientId());
        return executionService.executeWithPreTranslated(request, preTranslated, provider);
    }

    private static Map<String, Object> values(CqlExecutionResponse response) {
        Map<String, Object> out = new LinkedHashMap<>();
        response.getResults().forEach((name, result) -> out.put(name, result.getValue()));
        return out;
    }

    private static List<Double> sorted(List<Double> values) {
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    private static PopulationDefinition population(String type, String expression) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expression).build();
    }

    // ───────────────────────────────────────────────────────────── generator + oracle

    /** What the generator planted, computed independently of the engine. */
    record Oracle(boolean hasDiabetes, boolean hasFinalHbA1c, List<Double> hbA1cValues, int finishedEncounters,
                  int encounters, int conditions, int observations, int medicationRequests, String gender) {
    }

    record Generated(String patientId, List<Resource> resources, Oracle oracle, String summary) {
        @Override
        public String toString() {
            return summary;
        }
    }

    static Generated generate(Random r) {
        String pid = "p-" + Fuzz.between(r, 1, 999_999);
        List<Resource> resources = new ArrayList<>();
        StringBuilder summary = new StringBuilder("patient ").append(pid);

        Patient patient = new Patient();
        patient.setId(pid);
        String gender = Fuzz.pick(r, "male", "female", "other", "unknown", null);
        if (gender != null) patient.setGender(Enumerations.AdministrativeGender.fromCode(gender));
        if (Fuzz.chance(r, 0.8)) patient.setBirthDateElement(new DateType(randomDate(r, 1930, 2024)));
        if (Fuzz.chance(r, 0.5)) patient.addName().setFamily("王").addGiven("小明");
        if (Fuzz.chance(r, 0.3)) {
            patient.setDeceased(Fuzz.chance(r, 0.5) ? new BooleanType(r.nextBoolean()) : new DateTimeType(randomDateTime(r)));
        }
        if (Fuzz.chance(r, 0.3)) patient.setMultipleBirth(Fuzz.chance(r, 0.5) ? new BooleanType(true) : new IntegerType(2));
        resources.add(patient);
        summary.append(" gender=").append(gender);

        boolean hasDiabetes = false;
        int conditions = Fuzz.between(r, 0, 4);
        summary.append(" conditions=[");
        for (int i = 0; i < conditions; i++) {
            Condition condition = new Condition();
            condition.setId("c-" + i);
            condition.setSubject(new Reference("Patient/" + pid));
            CodeableConcept concept = new CodeableConcept();
            int codings = Fuzz.between(r, 0, 3);
            for (int k = 0; k < codings; k++) {
                String system = Fuzz.pick(r, ICD10, ICD10, ICD10, SNOMED, "urn:oid:2.16.840.1.113883.6.90", null, "");
                String code = Fuzz.pick(r, "E11.9", "E10.65", "E08.01", "E13.9", "E12", "E09.52", "I10", "J45.909", "N18.3", "", null, "e11.9", "E1");
                Coding coding = new Coding();
                if (system != null) coding.setSystem(system);
                if (code != null) coding.setCode(code);
                if (Fuzz.chance(r, 0.4)) coding.setDisplay("display " + k);
                concept.addCoding(coding);
                if (ICD10.equals(system) && isDiabetesCode(code)) hasDiabetes = true;
                summary.append(system == null ? "-" : system.equals(ICD10) ? "icd" : "other").append(':').append(code).append(' ');
            }
            if (Fuzz.chance(r, 0.3)) concept.setText("糖尿病");
            if (codings > 0 || concept.hasText()) condition.setCode(concept);
            if (Fuzz.chance(r, 0.6)) {
                condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding(
                        "http://terminology.hl7.org/CodeSystem/condition-clinical", Fuzz.pick(r, "active", "resolved", "inactive"), null)));
            }
            switch (Fuzz.between(r, 0, 5)) {
                case 0 -> condition.setOnset(new DateTimeType(randomDateTime(r)));
                case 1 -> condition.setOnset(randomPeriod(r));
                case 2 -> condition.setOnset(new Age().setValue(Fuzz.between(r, 1, 90)).setUnit("a"));
                case 3 -> condition.setOnset(new StringType("childhood"));
                default -> { /* no onset */ }
            }
            resources.add(condition);
            summary.append("; ");
        }
        summary.append(']');

        boolean hasFinalHbA1c = false;
        List<Double> hbA1cValues = new ArrayList<>();
        int observations = Fuzz.between(r, 0, 5);
        summary.append(" observations=[");
        for (int i = 0; i < observations; i++) {
            Observation observation = new Observation();
            observation.setId("o-" + i);
            observation.setSubject(new Reference("Patient/" + pid));
            Observation.ObservationStatus status = Fuzz.pick(r, Observation.ObservationStatus.FINAL, Observation.ObservationStatus.FINAL,
                    Observation.ObservationStatus.PRELIMINARY, Observation.ObservationStatus.AMENDED,
                    Observation.ObservationStatus.CANCELLED, Observation.ObservationStatus.ENTEREDINERROR, null);
            if (status != null) observation.setStatus(status);
            CodeableConcept concept = new CodeableConcept();
            int codings = Fuzz.between(r, 0, 3);
            boolean isHbA1c = false;
            for (int k = 0; k < codings; k++) {
                String system = Fuzz.pick(r, LOINC, LOINC, "http://example.org/labs", null);
                String code = Fuzz.pick(r, HBA1C, HBA1C, "13980-8", "2339-0", "4544-3", "", null, "4548-4 ");
                Coding coding = new Coding();
                if (system != null) coding.setSystem(system);
                if (code != null) coding.setCode(code);
                concept.addCoding(coding);
                if (HBA1C.equals(code)) isHbA1c = true;
            }
            if (codings > 0) observation.setCode(concept);
            Quantity plantedQuantity = null;
            switch (Fuzz.between(r, 0, 7)) {
                case 0, 1 -> {
                    Quantity quantity = new Quantity();
                    if (Fuzz.chance(r, 0.9)) quantity.setValue(randomDecimal(r));
                    String unit = Fuzz.pick(r, "%", "mmol/mol", "", null, "{score}", "mg/dL");
                    if (unit != null) quantity.setUnit(unit);
                    if (unit != null && Fuzz.chance(r, 0.5)) quantity.setSystem("http://unitsofmeasure.org").setCode(unit);
                    observation.setValue(quantity);
                    plantedQuantity = quantity;
                }
                case 2 -> observation.setValue(new CodeableConcept().addCoding(new Coding(SNOMED, "260385009", "Negative")));
                case 3 -> observation.setValue(new StringType("normal"));
                case 4 -> observation.setValue(new IntegerType(Fuzz.between(r, 0, 200)));
                case 5 -> observation.setValue(new BooleanType(r.nextBoolean()));
                case 6 -> observation.setValue(new Ratio().setNumerator(new Quantity().setValue(1)).setDenominator(new Quantity().setValue(2)));
                default -> { /* no value */ }
            }
            switch (Fuzz.between(r, 0, 4)) {
                case 0, 1 -> observation.setEffective(new DateTimeType(randomDateTime(r)));
                case 2 -> observation.setEffective(randomPeriod(r));
                case 3 -> observation.setEffective(new InstantType(String.format("%04d-%02d-%02dT10:00:00.000Z", Fuzz.between(r, 2024, 2025), Fuzz.between(r, 1, 12), Fuzz.between(r, 1, 28))));
                default -> { /* no effective */ }
            }
            boolean counted = isHbA1c && status == Observation.ObservationStatus.FINAL;
            if (counted) {
                hasFinalHbA1c = true;
                if (plantedQuantity != null && plantedQuantity.hasValue()) hbA1cValues.add(plantedQuantity.getValue().doubleValue());
            }
            resources.add(observation);
            summary.append(isHbA1c ? "hba1c" : "other").append('/').append(status == null ? "-" : status.toCode())
                    .append('/').append(plantedQuantity == null ? "nonquantity" : plantedQuantity.hasValue() ? plantedQuantity.getValue() : "novalue").append("; ");
        }
        summary.append(']');

        int finished = 0;
        int encounters = Fuzz.between(r, 0, 4);
        summary.append(" encounters=[");
        for (int i = 0; i < encounters; i++) {
            Encounter encounter = new Encounter();
            encounter.setId("e-" + i);
            encounter.setSubject(new Reference("Patient/" + pid));
            Encounter.EncounterStatus status = Fuzz.pick(r, Encounter.EncounterStatus.FINISHED, Encounter.EncounterStatus.FINISHED,
                    Encounter.EncounterStatus.FINISHED, Encounter.EncounterStatus.INPROGRESS, Encounter.EncounterStatus.PLANNED,
                    Encounter.EncounterStatus.CANCELLED, null);
            if (status != null) encounter.setStatus(status);
            String encounterClass = Fuzz.pick(r, "AMB", "IMP", "EMER", null);
            if (encounterClass != null) encounter.setClass_(new Coding(ACT_CODE, encounterClass, null));
            Period period = randomPeriod(r);
            if (period != null) encounter.setPeriod(period);
            if (status == Encounter.EncounterStatus.FINISHED) finished++;
            resources.add(encounter);
            summary.append(status == null ? "-" : status.toCode()).append('/').append(encounterClass).append('/')
                    .append(period == null ? "noperiod" : period.hasStart() && period.hasEnd() ? "both" : period.hasStart() ? "start" : "end").append("; ");
        }
        summary.append(']');

        int medicationRequests = Fuzz.between(r, 0, 3);
        for (int i = 0; i < medicationRequests; i++) {
            MedicationRequest request = new MedicationRequest();
            request.setId("m-" + i);
            request.setSubject(new Reference("Patient/" + pid));
            MedicationRequest.MedicationRequestStatus status = Fuzz.pick(r, MedicationRequest.MedicationRequestStatus.ACTIVE,
                    MedicationRequest.MedicationRequestStatus.COMPLETED, MedicationRequest.MedicationRequestStatus.STOPPED, null);
            if (status != null) request.setStatus(status);
            MedicationRequest.MedicationRequestIntent intent = Fuzz.pick(r, MedicationRequest.MedicationRequestIntent.ORDER,
                    MedicationRequest.MedicationRequestIntent.PLAN, null);
            if (intent != null) request.setIntent(intent);
            switch (Fuzz.between(r, 0, 2)) {
                case 0 -> request.setMedication(new CodeableConcept().addCoding(new Coding(ATC, Fuzz.pick(r, "A10BA02", "C09AA02"), null)));
                case 1 -> request.setMedication(new Reference("Medication/med-" + i));
                default -> { /* invalid: no medication[x] at all */ }
            }
            if (Fuzz.chance(r, 0.7)) request.setAuthoredOnElement(new DateTimeType(randomDateTime(r)));
            resources.add(request);
        }
        summary.append(" medicationRequests=").append(medicationRequests);

        Oracle oracle = new Oracle(hasDiabetes, hasFinalHbA1c, hbA1cValues, finished, encounters, conditions, observations,
                medicationRequests, gender);
        return new Generated(pid, resources, oracle, summary.toString());
    }

    private static boolean isDiabetesCode(String code) {
        if (code == null) return false;
        for (String prefix : List.of("E08", "E09", "E10", "E11", "E12", "E13")) {
            if (code.startsWith(prefix)) return true;
        }
        return false;
    }

    private static BigDecimal randomDecimal(Random r) {
        return BigDecimal.valueOf(Fuzz.between(r, 30, 1500), Fuzz.between(r, 0, 2));
    }

    /** A FHIR date with random precision: {@code 2025}, {@code 2025-06} or {@code 2025-06-15}. */
    private static String randomDate(Random r, int fromYear, int toYear) {
        int year = Fuzz.between(r, fromYear, toYear);
        return switch (Fuzz.between(r, 0, 3)) {
            case 0 -> String.format("%04d", year);
            case 1 -> String.format("%04d-%02d", year, Fuzz.between(r, 1, 12));
            default -> String.format("%04d-%02d-%02d", year, Fuzz.between(r, 1, 12), Fuzz.between(r, 1, 28));
        };
    }

    /** A FHIR dateTime around the measurement period, sometimes partial, sometimes without a zone. */
    private static String randomDateTime(Random r) {
        String date = String.format("%04d-%02d-%02d", Fuzz.between(r, 2024, 2026), Fuzz.between(r, 1, 12), Fuzz.between(r, 1, 28));
        return switch (Fuzz.between(r, 0, 5)) {
            case 0 -> date;
            case 1 -> date.substring(0, 7);
            case 2 -> date + "T" + String.format("%02d:%02d:%02d", Fuzz.between(r, 0, 23), Fuzz.between(r, 0, 59), Fuzz.between(r, 0, 59));
            case 3 -> date + "T" + String.format("%02d:%02d:%02d.%03d", Fuzz.between(r, 0, 23), Fuzz.between(r, 0, 59), Fuzz.between(r, 0, 59), Fuzz.between(r, 0, 999)) + "+08:00";
            default -> date + "T" + String.format("%02d:%02d:%02d", Fuzz.between(r, 0, 23), Fuzz.between(r, 0, 59), Fuzz.between(r, 0, 59)) + Fuzz.pick(r, "+08:00", "Z");
        };
    }

    /**
     * A period: both ends (end on or after start, same zone), start only, end only, or none. A period
     * whose end precedes its start is invalid FHIR and the engine rejects such an interval, so it is
     * not generated.
     */
    private static Period randomPeriod(Random r) {
        LocalDate start = LocalDate.of(Fuzz.between(r, 2024, 2026), Fuzz.between(r, 1, 12), Fuzz.between(r, 1, 28));
        LocalDate end = start.plusDays(Fuzz.between(r, 0, 10));
        String zone = Fuzz.pick(r, "+08:00", "Z");
        String startText = start + "T" + String.format("%02d:%02d:00", Fuzz.between(r, 0, 23), Fuzz.between(r, 0, 59)) + zone;
        String endText = end + "T23:59:00" + zone;
        return switch (Fuzz.between(r, 0, 4)) {
            case 0, 1 -> new Period().setStartElement(new DateTimeType(startText)).setEndElement(new DateTimeType(endText));
            case 2 -> new Period().setStartElement(new DateTimeType(startText));
            case 3 -> new Period().setEndElement(new DateTimeType(endText));
            default -> null;
        };
    }

    // ───────────────────────────────────────────────────────────── wiring

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    /** No terminology: the library uses a direct code, the engine tolerates a null provider. */
    static class NoOpTerminologyService extends FhirTerminologyService {
        NoOpTerminologyService() {
            super(FhirContext.forR4(), null, null, null);
        }

        @Override
        public TerminologyProvider createTerminologyProvider(String terminologyServerUrl) {
            return null;
        }

        @Override
        public TerminologyProvider createTerminologyProvider(String terminologyServerUrl, Map<String, String> pins) {
            return null;
        }
    }
}
