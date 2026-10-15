package com.cqlplatform.service.cql;

import com.cqlplatform.testsupport.Fuzz;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencds.cqf.cql.engine.runtime.ClassInstance;
import org.opencds.cqf.cql.engine.runtime.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-265 — {@link CqlValues}, the only bridge between the platform's plain Java and the cql-engine 5.x
 * value model (PAT-231), under random values: {@code unwrap(wrap(x)) == x} for every plain value the
 * platform passes as a parameter, foreign objects are refused loudly, and FHIR resources / primitives
 * convert to what the serialisation layer expects.
 */
@DisplayName("PAT-265 — CqlValues bridge properties")
class CqlValuesPropertyTest {

    static Object randomPlain(Random r, int depth) {
        switch (Fuzz.between(r, 0, depth > 1 ? 4 : 5)) {
            case 0: return r.nextBoolean();
            case 1: return Fuzz.between(r, -100_000, 100_000);
            case 2: return r.nextLong();
            case 3: return BigDecimal.valueOf(Fuzz.between(r, -1_000_000, 1_000_000), Fuzz.between(r, 0, 6));
            case 4: return Fuzz.pick(r, "", "text", "中文", "quote\"d", "a\nb", "😀");
            default: {
                List<Object> list = new ArrayList<>();
                int n = Fuzz.between(r, 0, 4);
                for (int i = 0; i < n; i++) list.add(Fuzz.chance(r, 0.1) ? null : randomPlain(r, depth + 1));
                return list;
            }
        }
    }

    @Test
    @DisplayName("unwrap(wrap(x)) is x for booleans, integers, longs, decimals, strings and nested lists")
    void wrapThenUnwrap_isTheIdentity() {
        Fuzz.forAll(20261070L, 1000, r -> Fuzz.chance(r, 0.05) ? null : randomPlain(r, 0), (plain, r) -> {
            Value wrapped = CqlValues.wrap(plain);
            if (plain == null) {
                assertThat(wrapped).isNull();
                return;
            }
            assertThat(wrapped).isNotNull();
            assertThat(CqlValues.unwrap(wrapped)).isEqualTo(plain);
            assertThat(CqlValues.wrap(wrapped)).as("a Value passes through").isSameAs(wrapped);
            assertThat(CqlValues.unwrap(plain)).as("unwrap of a plain value is itself").isSameAs(plain);
        });
    }

    @Test
    @DisplayName("doubles become decimals with the same text")
    void doubles_becomeDecimals() {
        Fuzz.forAll(20261071L, 300, r -> Fuzz.pick(r, r.nextDouble() * 1e6, (double) Fuzz.between(r, -500, 500), 0.1 + 0.2, 1e-9, 9.2), (d, r) -> {
            Object unwrapped = CqlValues.unwrap(CqlValues.wrap(d));
            assertThat(unwrapped).isEqualTo(new BigDecimal(Double.toString(d)));
            assertThat(CqlValues.unwrap(CqlValues.wrap(d.floatValue()))).isEqualTo(new BigDecimal(Float.toString(d.floatValue())));
        });
    }

    @Test
    @DisplayName("anything that is not a plain value or an engine value is refused, not silently dropped")
    void wrap_refusesForeignObjects() {
        Fuzz.forAll(20261072L, 40, r -> Fuzz.pick(r, new Object(), LocalDate.of(2025, 1, 1), new Patient(), Map.of("a", 1), new int[]{1}, 'c'), (foreign, r) ->
                assertThatThrownBy(() -> CqlValues.wrap(foreign)).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @DisplayName("wrapAll keeps every key in order and wraps each value")
    void wrapAll_keepsKeysAndOrder() {
        Fuzz.forAll(20261073L, 300, r -> {
            Map<String, Object> parameters = new LinkedHashMap<>();
            int n = Fuzz.between(r, 0, 6);
            for (int i = 0; i < n; i++) parameters.put("p" + Fuzz.between(r, 0, 20), Fuzz.chance(r, 0.1) ? null : randomPlain(r, 0));
            return parameters;
        }, (parameters, r) -> {
            Map<String, Value> wrapped = CqlValues.wrapAll(parameters);
            assertThat(wrapped.keySet()).containsExactlyElementsOf(parameters.keySet());
            for (Map.Entry<String, Object> e : parameters.entrySet()) {
                assertThat(CqlValues.unwrap(wrapped.get(e.getKey()))).as(e.getKey()).isEqualTo(e.getValue());
            }
            assertThat(CqlValues.wrapAll(null)).isNull();
        });
    }

    record Fhir(Resource resource, String type, String id) {
    }

    @Test
    @DisplayName("a FHIR resource converts to a ClassInstance described as FHIR.<Type>/<id>")
    void fromFhir_describesResources() {
        Fuzz.forAll(20261074L, 200, r -> {
            String id = Fuzz.chance(r, 0.2) ? null : Fuzz.pick(r, "1", "abc-123", "p-" + Fuzz.between(r, 1, 9999));
            Resource resource = switch (Fuzz.between(r, 0, 3)) {
                case 0 -> new Patient().setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
                case 1 -> new Condition().setCode(new org.hl7.fhir.r4.model.CodeableConcept().addCoding(new Coding("s", "c", null)));
                case 2 -> new Observation().setValue(new Quantity().setValue(5.5).setUnit("%"));
                default -> new Encounter().setStatus(Encounter.EncounterStatus.FINISHED);
            };
            if (id != null) resource.setId(id);
            return new Fhir(resource, resource.fhirType(), id);
        }, (fhir, r) -> {
            Value value = CqlValues.fromFhir(fhir.resource());
            assertThat(value).isInstanceOf(ClassInstance.class);
            ClassInstance instance = (ClassInstance) value;
            assertThat(CqlValues.primitiveValue(instance)).as("a resource is not a primitive").isNull();
            assertThat(CqlValues.describe(instance)).isEqualTo(fhir.id() == null ? "FHIR." + fhir.type() : "FHIR." + fhir.type() + "/" + fhir.id());
            assertThat(CqlValues.fromFhir(value)).as("a Value passes through").isSameAs(value);
            assertThat(CqlValues.fromFhir(null)).isNull();
        });
    }

    record Primitive(Object hapi, Object expected) {
    }

    @Test
    @DisplayName("a FHIR primitive converts to its plain value (PAT-233); a complex datatype does not")
    void primitives_unwrapToTheirValue() {
        Fuzz.forAll(20261075L, 300, r -> switch (Fuzz.between(r, 0, 5)) {
            case 0 -> new Primitive(new StringType(Fuzz.pick(r, "x", "中文", " a ")), null);   // "" is no value in FHIR (and HAPI)
            case 1 -> new Primitive(new CodeType("female"), "female");
            case 2 -> new Primitive(new BooleanType(r.nextBoolean()), null);
            case 3 -> new Primitive(new IntegerType(Fuzz.between(r, -5, 5)), null);
            case 4 -> new Primitive(new DecimalType(BigDecimal.valueOf(Fuzz.between(r, 0, 999), 1)), null);
            default -> new Primitive(new UriType("http://example.org/" + Fuzz.between(r, 1, 9)), null);
        }, (primitive, r) -> {
            Value value = CqlValues.fromFhir(primitive.hapi());
            Object plain = value instanceof ClassInstance instance ? CqlValues.primitiveValue(instance) : CqlValues.unwrap(value);
            Object hapiValue = ((org.hl7.fhir.r4.model.PrimitiveType<?>) primitive.hapi()).getValue();
            if (primitive.hapi() instanceof StringType || primitive.hapi() instanceof CodeType || primitive.hapi() instanceof UriType) {
                assertThat(plain).isEqualTo(hapiValue);
            } else if (primitive.hapi() instanceof BooleanType) {
                assertThat(plain).isEqualTo(hapiValue);
            } else if (primitive.hapi() instanceof IntegerType) {
                assertThat(plain).isEqualTo(hapiValue);
            } else {
                assertThat(plain).isInstanceOf(BigDecimal.class);
                assertThat(((BigDecimal) plain).compareTo((BigDecimal) hapiValue)).isZero();
            }
        });
    }

    @Test
    @DisplayName("complex datatypes are not primitives")
    void complexTypes_areNotPrimitives() {
        Fuzz.forAll(20261076L, 60, r -> Fuzz.pick(r,
                new Quantity().setValue(Fuzz.between(r, 1, 9)).setUnit("d"),
                new Identifier().setValue("v-" + Fuzz.between(r, 1, 9)),
                new Coding("s", "c", "d")), (datatype, r) -> {
            Value value = CqlValues.fromFhir(datatype);
            assertThat(value).isInstanceOf(ClassInstance.class);
            assertThat(CqlValues.primitiveValue((ClassInstance) value)).isNull();
        });
    }
}
