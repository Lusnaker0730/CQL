package com.cqlplatform.service.cql;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.opencds.cqf.cql.engine.runtime.ClassInstance;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.opencds.cqf.cql.engine.runtime.Tuple;
import org.opencds.cqf.cql.engine.runtime.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-231 — the one bridge between the engine's value model (cql-engine 5.x) and plain Java.
 * Everything the platform inspects about a CQL result goes through {@link CqlValues#unwrap};
 * a wrong mapping here does not fail loudly, it makes {@code instanceof Boolean} false and
 * every population silently empty — hence the round trips are locked one by one.
 */
class CqlValuesTest {

    @Test
    void primitives_roundTrip() {
        assertThat(CqlValues.unwrap(CqlValues.wrap(true))).isEqualTo(true);
        assertThat(CqlValues.unwrap(CqlValues.wrap(42))).isEqualTo(42);
        assertThat(CqlValues.unwrap(CqlValues.wrap(42L))).isEqualTo(42L);
        assertThat(CqlValues.unwrap(CqlValues.wrap(new BigDecimal("6.8")))).isEqualTo(new BigDecimal("6.8"));
        assertThat(CqlValues.unwrap(CqlValues.wrap(6.8d))).isEqualTo(new BigDecimal("6.8"));
        assertThat(CqlValues.unwrap(CqlValues.wrap("HbA1c"))).isEqualTo("HbA1c");
        assertThat(CqlValues.unwrap(null)).isNull();
        assertThat(CqlValues.wrap(null)).isNull();
    }

    @Test
    void unwrappedPrimitives_areTheJavaTypesTheEvaluatorTestsFor() {
        // PopulationEvaluator & friends decide membership with `instanceof Boolean` / `Boolean.TRUE.equals`.
        Object unwrapped = CqlValues.unwrap(new org.opencds.cqf.cql.engine.runtime.Boolean(true));
        assertThat(unwrapped).isInstanceOf(java.lang.Boolean.class).isEqualTo(Boolean.TRUE);
        assertThat(CqlValues.unwrap(new org.opencds.cqf.cql.engine.runtime.Integer(3))).isInstanceOf(java.lang.Integer.class);
        assertThat(CqlValues.unwrap(new org.opencds.cqf.cql.engine.runtime.String("x"))).isInstanceOf(java.lang.String.class);
    }

    @Test
    void lists_unwrapItemByItem_andWrapBack() {
        Object unwrapped = CqlValues.unwrap(CqlValues.wrap(List.of(1, "two", false)));
        assertThat(unwrapped).isInstanceOf(List.class);
        assertThat((List<?>) unwrapped).isEqualTo(List.of(1, "two", false));
    }

    @Test
    void tuples_becomeOrderedMaps() {
        Tuple tuple = new Tuple().withElements(new java.util.LinkedHashMap<>(Map.of(
                "score", new org.opencds.cqf.cql.engine.runtime.Integer(7))));
        tuple.getElements().put("label", new org.opencds.cqf.cql.engine.runtime.String("ok"));

        Object unwrapped = CqlValues.unwrap(tuple);

        assertThat(unwrapped).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) unwrapped;
        assertThat(map).containsEntry("score", 7).containsEntry("label", "ok");
    }

    @Test
    void engineNativeValues_passThroughWrap_untouched() {
        // The measurement period the platform builds is already an engine value.
        Interval period = new Interval(new DateTime(OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)), true,
                new DateTime(OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC)), true);
        assertThat(CqlValues.wrap(period)).isSameAs(period);
        assertThat(CqlValues.wrapAll(Map.of("Measurement Period", period))).containsEntry("Measurement Period", period);
        assertThat(CqlValues.wrapAll(null)).isNull();
    }

    @Test
    void somethingTheEngineCannotTake_isAProgrammingError_notANull() {
        assertThatThrownBy(() -> CqlValues.wrap(new Object())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Object");
    }

    @Test
    void fhirResources_becomeTypedStructuredValues_describedShortly() {
        Patient patient = new Patient();
        patient.setId("p-1");
        Value value = CqlValues.fromFhir(patient);

        assertThat(value).isInstanceOf(ClassInstance.class);
        assertThat(((ClassInstance) value).getTypeAsString()).endsWith("Patient");
        assertThat(CqlValues.typeName(value)).endsWith("Patient");
        // Display and logs get "FHIR.Patient/p-1", never the kilobytes of the whole tree.
        assertThat(CqlValues.describe((ClassInstance) value)).endsWith("Patient/p-1");
        assertThat(CqlValues.unwrap(value)).isSameAs(value); // structured values are not flattened
        assertThat(CqlValues.fromFhir(value)).isSameAs(value);
        assertThat(CqlValues.fromFhir(null)).isNull();
    }

    @Test
    void theTwoHapiDispatchProblemsOf4x_doNotSurviveConversion() {
        // Encounter.class collided with Object.getClass() under reflection (4.x resolvePath override).
        Encounter encounter = new Encounter();
        encounter.setId("e-1");
        encounter.getClass_().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("IMP");
        ClassInstance converted = (ClassInstance) CqlValues.fromFhir(encounter);
        assertThat(converted.getElements()).containsKey("class");
        assertThat(converted.getElements().get("class")).isInstanceOf(ClassInstance.class);

        // Enumeration<T> typed as the base code type raised "Ambiguous call to operator ToString" (BUG-106).
        MedicationRequest request = new MedicationRequest();
        request.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        ClassInstance mr = (ClassInstance) CqlValues.fromFhir(request);
        Value status = mr.getElements().get("status");
        assertThat(status).isNotNull();
        assertThat(status.getTypeAsString()).doesNotContain("Enumeration");
    }
}
