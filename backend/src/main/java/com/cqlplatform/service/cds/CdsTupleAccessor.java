package com.cqlplatform.service.cds;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Static utility for accessing CQL Tuple fields via reflection.
 * The CQL engine represents Tuples as objects with a {@code getElements()} method
 * that returns a {@code Map<String, Object>}. This class encapsulates the reflection
 * needed to access those fields without a compile-time dependency on the Tuple class.
 */
public final class CdsTupleAccessor {

    private CdsTupleAccessor() {
    }

    public static boolean isTuple(Object value) {
        if (value == null) {
            return false;
        }
        try {
            java.lang.reflect.Method m = value.getClass().getMethod("getElements");
            return Map.class.isAssignableFrom(m.getReturnType());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getElements(Object tuple) {
        try {
            java.lang.reflect.Method getElements = tuple.getClass().getMethod("getElements");
            return (Map<String, Object>) getElements.invoke(tuple);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static String getString(Object tuple, String fieldName) {
        Object val = getElements(tuple).get(fieldName);
        return val != null ? val.toString() : null;
    }

    public static Object getObject(Object tuple, String fieldName) {
        return getElements(tuple).get(fieldName);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Object tuple, String fieldName) {
        Object val = getObject(tuple, fieldName);
        if (val instanceof List) {
            return (List<Object>) val;
        }
        return Collections.emptyList();
    }

    public static Boolean getBoolean(Object tuple, String fieldName) {
        Object val = getObject(tuple, fieldName);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return null;
    }
}
