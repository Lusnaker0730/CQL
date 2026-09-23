package com.cqlplatform.service.measure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The category a (serialised) CQL value denotes when a report groups patients by it — the
 * stratum of a stratifier (PAT-233), the bucket of a supplemental datum or risk adjustment
 * factor (PAT-234). One rule for both, so a value that lands in stratum {@code female} also
 * lands in supplemental-data bucket {@code female}.
 *
 * <p>Serialisation already happened in {@code CqlExecutionService.toSerializable}: a FHIR
 * primitive such as {@code Patient.gender} arrives as its value, a CQL {@code Code} as a map,
 * a Tuple as a Map, a resource as {@code FHIR.Type/id}.
 */
public final class ValueKeys {

    /** Longest key kept; the report columns are VARCHAR(500) and a key is a label, not a payload. */
    public static final int MAX_KEY = 200;

    private ValueKeys() {
    }

    /**
     * <ul>
     *   <li>{@code null}, blank, the text {@code "null"}, an empty list → {@code null}: no category</li>
     *   <li>String / Boolean / Number → as text (numbers keep their own formatting, so
     *       {@code 65} and {@code 65.0} are different keys — return a label, not a measurement)</li>
     *   <li>Map with a {@code code} entry (a serialised Code / Coding) → the code, with
     *       {@code display} in parentheses when present</li>
     *   <li>Map with {@code codes} (a serialised Concept) → its display, else its codes</li>
     *   <li>List → the elements' keys joined with {@code ", "} (one category per distinct
     *       element is NOT supported; the author sees what came back)</li>
     * </ul>
     */
    public static String of(Object value) {
        if (value == null) return null;
        String key;
        if (value instanceof Map<?, ?> map && map.get("code") != null) {
            Object display = map.get("display");
            key = display != null && !String.valueOf(display).isBlank()
                    ? map.get("code") + " (" + display + ")" : String.valueOf(map.get("code"));
        } else if (value instanceof Map<?, ?> map && map.get("codes") instanceof Iterable<?> codes) {
            Object display = map.get("display");
            key = display != null && !String.valueOf(display).isBlank() ? String.valueOf(display) : String.valueOf(of(codes));
        } else if (value instanceof Iterable<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) {
                String part = of(item);
                if (part != null) parts.add(part);
            }
            if (parts.isEmpty()) return null;
            key = String.join(", ", parts);
        } else {
            key = String.valueOf(value);
        }
        key = key.trim();
        // Same skip rule the stratifier aggregation always had (a null value renders as "null").
        if (key.isEmpty() || "null".equals(key)) return null;
        return key.length() > MAX_KEY ? key.substring(0, MAX_KEY - 1) + "…" : key;
    }
}
