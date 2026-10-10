package com.cqlplatform.service.measure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PAT-243 — the identities of the episodes a population define returned.
 *
 * <p>An episode-based population (basis Encounter, Procedure, …) is a CQL define that returns
 * the list of episodes, and the evaluation counts those rather than the patient. The engine's
 * result reaches the evaluator serialised ({@code CqlExecutionService.toSerializable}): a FHIR
 * resource is its short description {@code FHIR.Encounter/123}, so a resource list arrives as a
 * list of such strings. The key is what two population defines of one group agree on — the
 * Denominator's episodes are the Initial Population's episodes that also satisfy the denominator
 * criteria — which is why {@link PopulationEvaluator} intersects keys instead of trusting counts.
 */
final class EpisodeKeys {

    private EpisodeKeys() {
    }

    /**
     * The episode keys in a serialised population value, in list order (duplicates kept), or
     * {@code null} when the value is not an identifiable list of resources: a Boolean, a number,
     * a list of scalars / tuples, or a resource without an id ({@code FHIR.Encounter} alone — two
     * such episodes could not be told apart). An empty list is a valid empty set.
     */
    static List<String> of(Object value) {
        if (value instanceof Iterable<?> items) {
            List<String> keys = new ArrayList<>();
            for (Object item : items) {
                String key = keyOf(item);
                if (key == null) return null;
                keys.add(key);
            }
            return keys;
        }
        String single = keyOf(value);
        return single == null ? null : List.of(single);
    }

    private static String keyOf(Object item) {
        if (item instanceof String s) {
            // "FHIR.Encounter/enc-1": type, a slash, a non-empty id
            int slash = s.indexOf('/');
            return s.startsWith("FHIR.") && slash > 5 && slash < s.length() - 1 ? s : null;
        }
        if (item instanceof Map<?, ?> m) {
            // a resource that reached us as a map (a tuple { resourceType, id }, or a hand-written define)
            Object type = m.get("resourceType");
            Object id = m.get("id");
            return type != null && id != null && !String.valueOf(id).isBlank() ? "FHIR." + type + "/" + id : null;
        }
        return null;
    }
}
