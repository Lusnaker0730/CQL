package com.cqlplatform.service.cql;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.service.authoring.CqlTemplateEngine;
import com.cqlplatform.service.authoring.ExpressionCqlEngine;
import com.cqlplatform.service.authoring.ExpressionCqlEngine.BuildContext;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden integration suite for modifier-generated CQL.
 *
 * <p><b>Why this file exists:</b> before this suite, our generator produced CQL
 * strings that were only validated by translator-level unit tests (does it parse?).
 * BUG-110 shipped because {@code (X as Period) overlaps Y} looks syntactically fine
 * at translation time but triggers {@code ToInterval(null)} dispatch ambiguity at
 * runtime when X is null or has a mismatched choice type.
 *
 * <p>This suite feeds every modifier's generated CQL through the real CQL engine
 * against varied golden FHIR resources (Period-typed, dateTime-typed, null effective,
 * in/out of Measurement Period) and asserts:
 * <ol>
 *   <li>Execution completes without runtime CqlException</li>
 *   <li>The produced list contains exactly the expected resources</li>
 * </ol>
 *
 * <p><b>No Mockito.</b> Integration tests should not need to mock the world. This
 * class constructs the real services with {@code null} dependencies where they are
 * not actually called by {@code executeWithProvider}. Runs cleanly on JDK 21 and 25.
 */
@DisplayName("Modifier-Generated CQL Golden Integration Suite")
class ModifierGeneratedCqlGoldenTest {

    private static final String HbA1c_LOINC = "17856-6";
    private static final String LOINC_SYSTEM = "http://loinc.org";

    private CqlExecutionService executionService;
    private ExpressionCqlEngine expressionEngine;
    private ModifierService modifierService;

    @BeforeEach
    void setUp() {
        CqlTemplateEngine templateEngine = new CqlTemplateEngine();
        modifierService = new ModifierService();
        modifierService.init();
        expressionEngine = new ExpressionCqlEngine(templateEngine, modifierService, new com.cqlplatform.service.authoring.CustomModifierCqlBuilder());

        // Real CqlExecutionService. We pass a no-op FhirTerminologyService (the engine still
        // needs a reference to call createTerminologyProvider, but our test CQL uses only direct
        // `code` declarations — no ValueSet expansion — so returning null is sufficient).
        // FhirDataProviderService is never touched when a PrefetchRetrieveProvider is supplied.
        executionService = new CqlExecutionService(
                new FhirDataProviderService(null, null, null),   // only needed for getAndResetRetrieveCount()
                new NoOpTerminologyService(),
                Executors.newSingleThreadExecutor(),
                null    // CqlLibraryRepository — null means no DatabaseLibrarySourceProvider
        );
        setField(executionService, "timeoutSeconds", 30);
        setField(executionService, "maxRetrieveCount", 10000);
        setField(executionService, "maxCollectionSize", 1000);
        setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DuringMeasurementPeriod — the BUG-110 regression set
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DuringMeasurementPeriod on Observation")
    class DuringMeasurementPeriodObservation {

        private String buildLibrary() {
            // Apply DuringMeasurementPeriodObservation to a raw [Observation] retrieve
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "DuringMeasurementPeriodObservation");
            mod.put("cqlTemplate", "DuringMeasurementPeriod");
            String fragment = expressionEngine.applyModifier(
                    "[Observation: \"HbA1c\"]", mod, new BuildContext(null, null));

            return "library MeasureTest version '1.0.0'\n"
                    + "using FHIR version '4.0.1'\n"
                    + "include FHIRHelpers version '4.0.1' called FHIRHelpers\n"
                    + "\n"
                    + "codesystem \"LOINC\": '" + LOINC_SYSTEM + "'\n"
                    + "code \"HbA1c\": '" + HbA1c_LOINC + "' from \"LOINC\"\n"
                    + "\n"
                    + "parameter \"Measurement Period\" Interval<DateTime>\n"
                    + "  default Interval[@2026-01-01T00:00:00.0, @2026-12-31T23:59:59.999]\n"
                    + "\n"
                    + "context Patient\n"
                    + "\n"
                    + "define \"Filtered\":\n"
                    + "  " + fragment + "\n"
                    + "define \"Count\": Count(\"Filtered\")\n";
        }

        @Test
        @DisplayName("Period effective inside MP → observation matches")
        void periodInsideMp_matches() {
            Resource obs = obsWithPeriod("in", "2026-06-10", "2026-06-15");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("Period effective entirely before MP → no match")
        void periodOutsideMp_noMatch() {
            Resource obs = obsWithPeriod("old", "2025-06-10", "2025-06-15");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(0);
        }

        @Test
        @DisplayName("Period effective partially overlapping MP boundary → matches (overlaps semantics)")
        void periodOverlapsBoundary_matches() {
            Resource obs = obsWithPeriod("edge", "2025-12-20", "2026-01-05");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("dateTime effective inside MP → matches (choice-type dateTime branch)")
        void dateTimeInsideMp_matches() {
            Resource obs = obsWithDateTime("dt-in", "2026-03-15T10:00:00Z");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("dateTime effective outside MP → no match")
        void dateTimeOutsideMp_noMatch() {
            Resource obs = obsWithDateTime("dt-out", "2024-03-15T10:00:00Z");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(0);
        }

        @Test
        @DisplayName("null effective → no match + no ToInterval(null) dispatch ambiguity (BUG-110)")
        void nullEffective_noExceptionNoMatch() {
            Resource obs = obsWithNoEffective("null-eff");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(obs));

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);  // the BUG-110 invariant
            assertThat(countResult(r)).isEqualTo(0);
        }

        @Test
        @DisplayName("Mixed list: only matching resources come back")
        void mixedList_returnsOnlyMatching() {
            List<Resource> all = List.of(
                    obsWithPeriod("in-1", "2026-06-10", "2026-06-15"),
                    obsWithPeriod("in-2", "2026-07-01", "2026-07-01"),
                    obsWithPeriod("before", "2025-06-10", "2025-06-15"),
                    obsWithPeriod("after", "2027-06-10", "2027-06-15"),
                    obsWithDateTime("dt-in", "2026-03-15T10:00:00Z"),
                    obsWithDateTime("dt-out", "2024-03-15T10:00:00Z"),
                    obsWithNoEffective("null-eff")
            );
            CqlExecutionResponse r = execute(buildLibrary(), all);

            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            // Expected matches: in-1, in-2, dt-in → 3
            assertThat(countResult(r)).isEqualTo(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DuringMeasurementPeriod — Encounter
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DuringMeasurementPeriod on Encounter")
    class DuringMeasurementPeriodEncounterTests {

        private String buildLibrary() {
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "DuringMeasurementPeriodEncounter");
            mod.put("cqlTemplate", "DuringMeasurementPeriod");
            String fragment = expressionEngine.applyModifier(
                    "[Encounter]", mod, new BuildContext(null, null));

            return "library EncounterTest version '1.0.0'\n"
                    + "using FHIR version '4.0.1'\n"
                    + "include FHIRHelpers version '4.0.1' called FHIRHelpers\n"
                    + "\n"
                    + "parameter \"Measurement Period\" Interval<DateTime>\n"
                    + "  default Interval[@2026-01-01T00:00:00.0, @2026-12-31T23:59:59.999]\n"
                    + "\n"
                    + "context Patient\n"
                    + "\n"
                    + "define \"Filtered\":\n"
                    + "  " + fragment + "\n"
                    + "define \"Count\": Count(\"Filtered\")\n";
        }

        @Test
        @DisplayName("period inside MP → matches")
        void periodInside() {
            Resource enc = encWithPeriod("in", "2026-06-10", "2026-06-15");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(enc));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("period before MP → no match")
        void periodOutside() {
            Resource enc = encWithPeriod("old", "2025-01-10", "2025-01-15");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(enc));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(0);
        }

        @Test
        @DisplayName("null period → no match + no exception (null-check branch)")
        void nullPeriod() {
            Encounter enc = new Encounter();
            enc.setId("no-period");
            enc.setStatus(Encounter.EncounterStatus.FINISHED);
            // no setPeriod → E.period is null; the generated whereClause guards with
            // `E.period is not null and FHIRHelpers.ToInterval(E.period) ...`
            CqlExecutionResponse r = execute(buildLibrary(), List.of(enc));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DuringMeasurementPeriod — Condition
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DuringMeasurementPeriod on Condition")
    class DuringMeasurementPeriodConditionTests {

        private String buildLibrary() {
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "DuringMeasurementPeriodCondition");
            mod.put("cqlTemplate", "DuringMeasurementPeriod");
            String fragment = expressionEngine.applyModifier(
                    "[Condition]", mod, new BuildContext(null, null));

            return "library ConditionTest version '1.0.0'\n"
                    + "using FHIR version '4.0.1'\n"
                    + "include FHIRHelpers version '4.0.1' called FHIRHelpers\n"
                    + "\n"
                    + "parameter \"Measurement Period\" Interval<DateTime>\n"
                    + "  default Interval[@2026-01-01T00:00:00.0, @2026-12-31T23:59:59.999]\n"
                    + "\n"
                    + "context Patient\n"
                    + "\n"
                    + "define \"Filtered\":\n"
                    + "  " + fragment + "\n"
                    + "define \"Count\": Count(\"Filtered\")\n";
        }

        @Test
        @DisplayName("onset as dateTime inside MP → matches")
        void onsetDateTimeInside() {
            Condition c = newCondition("onset-in");
            c.setOnset(new DateTimeType("2026-06-15"));
            CqlExecutionResponse r = execute(buildLibrary(), List.of(c));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("onset as Period inside MP → matches")
        void onsetPeriodInside() {
            Condition c = newCondition("onset-period");
            Period p = new Period();
            p.setStartElement(new DateTimeType("2026-03-01"));
            p.setEndElement(new DateTimeType("2026-03-05"));
            c.setOnset(p);
            CqlExecutionResponse r = execute(buildLibrary(), List.of(c));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("no onset, recordedDate inside MP → matches via recordedDate fallback branch")
        void recordedDateFallback() {
            Condition c = newCondition("rec-date");
            c.setRecordedDateElement(new DateTimeType("2026-04-15"));
            CqlExecutionResponse r = execute(buildLibrary(), List.of(c));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(1);
        }

        @Test
        @DisplayName("no onset and no recordedDate → no match + no exception")
        void noDateAtAll() {
            Condition c = newCondition("no-dates");
            CqlExecutionResponse r = execute(buildLibrary(), List.of(c));
            assertThat(r.isSuccess()).isTrue();
            assertNoDispatchExceptions(r);
            assertThat(countResult(r)).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Explicit BUG-110 regression lock
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BUG-110 regression lock — ToInterval(null) must never surface")
    class Bug110RegressionLock {

        @Test
        @DisplayName("Observation.effective null + DuringMeasurementPeriodObservation must not throw Ambiguous ToInterval(null)")
        void bug110_nullEffective_doesNotThrow() {
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "DuringMeasurementPeriodObservation");
            mod.put("cqlTemplate", "DuringMeasurementPeriod");
            String fragment = expressionEngine.applyModifier(
                    "[Observation]", mod, new BuildContext(null, null));

            String cql = "library Bug110 version '1.0.0'\n"
                    + "using FHIR version '4.0.1'\n"
                    + "include FHIRHelpers version '4.0.1' called FHIRHelpers\n"
                    + "parameter \"Measurement Period\" Interval<DateTime>\n"
                    + "  default Interval[@2026-01-01T00:00:00.0, @2026-12-31T23:59:59.999]\n"
                    + "context Patient\n"
                    + "define \"Filtered\": " + fragment + "\n";

            Observation nullEff = new Observation();
            nullEff.setId("bug110");
            nullEff.setStatus(Observation.ObservationStatus.FINAL);
            // No setEffective

            CqlExecutionResponse r = execute(cql, List.of(nullEff));
            assertThat(r.isSuccess()).isTrue();
            // The invariant under test: no dispatch-ambiguity error surfaces
            assertNoDispatchExceptions(r);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private CqlExecutionResponse execute(String cql, List<Resource> resources) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(cql);
        request.setPatientId("patient-test");
        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(resources, "patient-test");
        return executionService.executeWithProvider(request, provider);
    }

    /**
     * Asserts that no CQL runtime exception (Ambiguous / ToInterval / ToDateTime) surfaced
     * on any define in the result. This catches dispatch-ambiguity regressions.
     */
    private static void assertNoDispatchExceptions(CqlExecutionResponse r) {
        List<String> errs = r.getErrors() != null ? r.getErrors() : List.of();
        for (String err : errs) {
            assertThat(err)
                    .as("Runtime dispatch error leaked: %s", err)
                    .doesNotContain("Ambiguous call")
                    .doesNotContain("ToInterval(null)");
        }
    }

    private static int countResult(CqlExecutionResponse r) {
        ExpressionResult cnt = r.getResults().get("Count");
        if (cnt == null || cnt.getValue() == null) return -1;
        Object v = cnt.getValue();
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static Resource obsWithPeriod(String id, String startDate, String endDate) {
        Observation o = newHbA1c(id);
        Period p = new Period();
        p.setStartElement(new DateTimeType(startDate));
        p.setEndElement(new DateTimeType(endDate));
        o.setEffective(p);
        return o;
    }

    private static Resource obsWithDateTime(String id, String isoDateTime) {
        Observation o = newHbA1c(id);
        o.setEffective(new DateTimeType(isoDateTime));
        return o;
    }

    private static Resource obsWithNoEffective(String id) {
        return newHbA1c(id);   // no setEffective → null choice
    }

    private static Observation newHbA1c(String id) {
        Observation o = new Observation();
        o.setId(id);
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.getCode().addCoding()
                .setSystem(LOINC_SYSTEM)
                .setCode(HbA1c_LOINC);
        o.getSubject().setReference("Patient/patient-test");
        return o;
    }

    private static Resource encWithPeriod(String id, String startDate, String endDate) {
        Encounter e = new Encounter();
        e.setId(id);
        e.setStatus(Encounter.EncounterStatus.FINISHED);
        Period p = new Period();
        p.setStartElement(new DateTimeType(startDate));
        p.setEndElement(new DateTimeType(endDate));
        e.setPeriod(p);
        e.getSubject().setReference("Patient/patient-test");
        return e;
    }

    private static Condition newCondition(String id) {
        Condition c = new Condition();
        c.setId(id);
        CodeableConcept cc = new CodeableConcept();
        cc.addCoding(new Coding().setSystem("http://hl7.org/fhir/sid/icd-10-cm").setCode("E11.9"));
        c.setCode(cc);
        c.getSubject().setReference("Patient/patient-test");
        return c;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Minimal FhirTerminologyService used when the test CQL only references direct codes
     * (no ValueSet expansion). Returning null from createTerminologyProvider is tolerated by
     * the CQL engine's {@link org.opencds.cqf.cql.engine.execution.Environment}.
     */
    static class NoOpTerminologyService extends FhirTerminologyService {
        NoOpTerminologyService() {
            super(FhirContext.forR4(), null, null, null);
        }

        @Override
        public TerminologyProvider createTerminologyProvider(String terminologyServerUrl) {
            return null;
        }
    }
}
