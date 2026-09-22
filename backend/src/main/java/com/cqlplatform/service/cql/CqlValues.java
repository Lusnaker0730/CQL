package com.cqlplatform.service.cql;

import org.opencds.cqf.cql.engine.runtime.ClassInstance;
import org.opencds.cqf.cql.engine.runtime.Tuple;
import org.opencds.cqf.cql.engine.runtime.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge between the CQL engine's runtime value model and plain Java.
 *
 * <p>Since cql-engine 5.x every value the engine produces or consumes is a
 * {@link Value}: {@code runtime.Boolean} instead of {@code java.lang.Boolean},
 * {@code runtime.List} instead of {@code java.util.List}, and FHIR resources as a
 * {@link ClassInstance} tree (converted by the model resolver) instead of HAPI objects.
 * The platform keeps its own code on plain Java: everything that inspects a result
 * ({@code instanceof Boolean}, list sizes, tuple maps) goes through {@link #unwrap},
 * and everything that hands the engine a FHIR resource (prefetch, fallback search)
 * goes through {@link #fromFhir}. Keep this the ONLY place that knows both worlds.
 */
public final class CqlValues {

    /** One resolver for the process: conversion needs HAPI's runtime definitions, which are expensive to build. */
    private static final ComparableR4FhirModelResolver RESOLVER = new ComparableR4FhirModelResolver();

    private CqlValues() {
    }

    public static ComparableR4FhirModelResolver resolver() {
        return RESOLVER;
    }

    /** A HAPI resource (or datatype) as the engine's structured value. */
    public static Value fromFhir(Object hapi) {
        if (hapi == null) return null;
        if (hapi instanceof Value v) return v;
        return RESOLVER.toCqlValue(hapi, false);
    }

    /**
     * A plain-Java value as an engine value — for CQL parameters that arrive as JSON
     * (strings, numbers, booleans) or that the platform builds itself (an {@code Interval} of
     * {@code DateTime}s is already a {@link Value} and passes through). Anything else is a
     * programming error, not a user error: say so instead of handing the engine a null.
     */
    public static Value wrap(Object plain) {
        if (plain == null) return null;
        if (plain instanceof Value v) return v;
        if (plain instanceof java.lang.Boolean b) return new org.opencds.cqf.cql.engine.runtime.Boolean(b);
        if (plain instanceof java.lang.Integer i) return new org.opencds.cqf.cql.engine.runtime.Integer(i);
        if (plain instanceof java.lang.Long l) return new org.opencds.cqf.cql.engine.runtime.Long(l);
        if (plain instanceof java.math.BigDecimal d) return new org.opencds.cqf.cql.engine.runtime.Decimal(d);
        if (plain instanceof java.lang.Double || plain instanceof java.lang.Float) {
            return new org.opencds.cqf.cql.engine.runtime.Decimal(new java.math.BigDecimal(plain.toString()));
        }
        if (plain instanceof java.lang.String s) return new org.opencds.cqf.cql.engine.runtime.String(s);
        if (plain instanceof Iterable<?> iterable) {
            List<Value> items = new ArrayList<>();
            for (Object item : iterable) items.add(wrap(item));
            return new org.opencds.cqf.cql.engine.runtime.List(items);
        }
        throw new IllegalArgumentException("Cannot pass a " + plain.getClass().getName() + " to the CQL engine as a value");
    }

    /** {@link #wrap} over a parameter map; a null map stays null. */
    public static Map<String, Value> wrapAll(Map<String, Object> parameters) {
        if (parameters == null) return null;
        Map<String, Value> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : parameters.entrySet()) out.put(e.getKey(), wrap(e.getValue()));
        return out;
    }

    /**
     * The plain-Java equivalent of an engine value: primitives unwrapped, lists as
     * {@link List} of unwrapped items, tuples as ordered maps. Structured FHIR values
     * ({@link ClassInstance}) and the engine's own temporal / quantity / code types are
     * returned as they are — callers format those by type.
     */
    public static Object unwrap(Object value) {
        if (value == null) return null;
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Boolean b) return b.getValue();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Integer i) return i.getValue();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Long l) return l.getValue();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Decimal d) return d.getValue();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.String s) return s.getValue();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.List list) {
            List<Object> out = new ArrayList<>();
            for (Value item : list.getValue()) out.add(unwrap(item));
            return out;
        }
        if (value instanceof Tuple tuple) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Value> e : tuple.getElements().entrySet()) out.put(e.getKey(), unwrap(e.getValue()));
            return out;
        }
        return value;
    }

    /**
     * A one-line description of a structured value for display and logs: {@code FHIR.Patient/123}
     * when it has an id, else just its type. A Patient's ClassInstance tree can run to kilobytes;
     * the 4.x path showed HAPI's {@code Patient@hash}, and this is the equivalent.
     */
    public static String describe(ClassInstance instance) {
        Value id = instance.getElements().get("id");
        Object plainId = id == null ? null : unwrap(id);
        if (plainId instanceof ClassInstance idElement) plainId = unwrap(idElement.getElements().get("value"));
        return plainId != null ? instance.getTypeAsString() + "/" + plainId : instance.getTypeAsString();
    }

    /** The CQL-facing type name of a value ({@code Boolean}, {@code List}, {@code FHIR.Encounter}…), for display. */
    public static String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof ClassInstance ci) return ci.getTypeAsString();
        Object plain = unwrap(value);
        if (plain instanceof Map) return "Tuple";
        return plain.getClass().getSimpleName();
    }
}
