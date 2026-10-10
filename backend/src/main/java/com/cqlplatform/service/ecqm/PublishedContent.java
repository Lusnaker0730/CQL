package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.util.ContentHash;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * PAT-238 — the fingerprint of what a publish puts on a measure: its CQL and its group
 * definitions (population → define mapping, stratifiers, observations). Normalised so that a
 * round trip through the database (or a save on the measure page that changes nothing) hashes
 * the same: line endings unified, trailing whitespace dropped, nulls / empty arrays / empty
 * objects removed and object keys sorted.
 */
public final class PublishedContent {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private PublishedContent() {
    }

    public static String hash(String cql, List<GroupDefinition> groups) {
        String normalisedCql = cql == null ? "" : cql.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        JsonNode tree = MAPPER.valueToTree(groups == null ? List.of() : groups);
        prune(tree);
        String groupsJson;
        try {
            groupsJson = MAPPER.writeValueAsString(MAPPER.treeToValue(tree, Object.class));
        } catch (Exception e) {
            groupsJson = tree.toString();
        }
        return ContentHash.sha256Hex(normalisedCql + "\n--groups--\n" + groupsJson);
    }

    /** Remove nulls, empty arrays and empty objects, depth first. */
    private static boolean prune(JsonNode node) {
        if (node == null || node.isNull()) return true;
        if (node instanceof ObjectNode object) {
            List<String> drop = new ArrayList<>();
            Iterator<String> names = object.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (prune(object.get(name))) drop.add(name);
            }
            object.remove(drop);
            return object.isEmpty();
        }
        if (node instanceof ArrayNode array) {
            for (int i = array.size() - 1; i >= 0; i--) {
                JsonNode item = array.get(i);
                if (item == null || item.isNull()) array.remove(i);
                else prune(item);
            }
            return array.isEmpty();
        }
        return false;
    }
}
