package com.cqlplatform.service.cql;

import com.cqlplatform.model.measure.DataRequirementInfo;
import com.cqlplatform.model.measure.DataRequirementInfo.CodeFilterInfo;
import com.cqlplatform.model.measure.DataRequirementInfo.CodingInfo;
import com.cqlplatform.model.measure.DataRequirementInfo.DateFilterInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Extracts FHIR DataRequirement resources from ELM JSON by walking the
 * expression tree and collecting all Retrieve elements.
 */
@Service
@Slf4j
public class DataRequirementExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FHIR_NS_PREFIX = "{http://hl7.org/fhir}";

    /**
     * Extract DataRequirement entries from ELM JSON.
     *
     * @param elmJson the ELM JSON string produced by CQL translation
     * @return deduplicated list of DataRequirementInfo objects
     */
    public List<DataRequirementInfo> extract(String elmJson) {
        if (elmJson == null || elmJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = MAPPER.readTree(elmJson);

            // Build a map of ValueSetDef name -> id (OID/URL) from the library's valueSets section
            Map<String, String> valueSetMap = buildValueSetMap(root);

            // Collect all Retrieve nodes
            List<RetrieveInfo> retrieves = new ArrayList<>();
            collectRetrieves(root, retrieves);

            // Convert to DataRequirementInfo and deduplicate
            return deduplicateRequirements(retrieves, valueSetMap);
        } catch (Exception e) {
            log.warn("Failed to extract data requirements from ELM JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Build a mapping from ValueSetDef name to its id (OID or URL).
     * ELM structure: library.valueSets.def[] with {name, id} fields.
     */
    private Map<String, String> buildValueSetMap(JsonNode root) {
        Map<String, String> map = new HashMap<>();
        JsonNode valueSets = root.path("library").path("valueSets").path("def");
        if (valueSets.isArray()) {
            for (JsonNode vs : valueSets) {
                String name = vs.path("name").asText(null);
                String id = vs.path("id").asText(null);
                if (name != null && id != null) {
                    map.put(name, id);
                }
            }
        }
        return map;
    }

    /**
     * Recursively walk the ELM JSON tree and collect Retrieve nodes.
     */
    private void collectRetrieves(JsonNode node, List<RetrieveInfo> retrieves) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            String type = node.path("type").asText(null);
            if ("Retrieve".equals(type)) {
                RetrieveInfo info = parseRetrieve(node);
                if (info != null) {
                    retrieves.add(info);
                }
            }
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                collectRetrieves(it.next(), retrieves);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectRetrieves(child, retrieves);
            }
        }
    }

    /**
     * Parse a single Retrieve node into an intermediate RetrieveInfo object.
     */
    private RetrieveInfo parseRetrieve(JsonNode retrieveNode) {
        String dataType = retrieveNode.path("dataType").asText(null);
        if (dataType == null) {
            return null;
        }

        // Strip FHIR namespace prefix
        String resourceType = dataType.startsWith(FHIR_NS_PREFIX)
                ? dataType.substring(FHIR_NS_PREFIX.length())
                : dataType;

        String codeProperty = retrieveNode.path("codeProperty").asText(null);
        String dateProperty = retrieveNode.path("dateProperty").asText(null);

        // Extract code/valueSet references from the "codes" sub-element
        String valueSetRefName = null;
        List<CodingInfo> directCodes = new ArrayList<>();

        JsonNode codesNode = retrieveNode.get("codes");
        if (codesNode != null) {
            String codesType = codesNode.path("type").asText("");
            switch (codesType) {
                case "ValueSetRef":
                    valueSetRefName = codesNode.path("name").asText(null);
                    break;
                case "ToList":
                    // Single code wrapped in ToList: codes.operand is a CodeRef or code literal
                    JsonNode operand = codesNode.get("operand");
                    if (operand != null) {
                        extractCodeRef(operand, directCodes);
                    }
                    break;
                case "List":
                    // Multiple codes: codes.element[] contains CodeRef entries
                    JsonNode elements = codesNode.get("element");
                    if (elements != null && elements.isArray()) {
                        for (JsonNode elem : elements) {
                            extractCodeRef(elem, directCodes);
                        }
                    }
                    break;
                default:
                    // Could be a CodeRef directly
                    extractCodeRef(codesNode, directCodes);
                    break;
            }
        }

        RetrieveInfo info = new RetrieveInfo();
        info.resourceType = resourceType;
        info.codeProperty = codeProperty;
        info.dateProperty = dateProperty;
        info.valueSetRefName = valueSetRefName;
        info.directCodes = directCodes;
        return info;
    }

    /**
     * Extract a code reference from a CodeRef or code literal node.
     */
    private void extractCodeRef(JsonNode node, List<CodingInfo> codes) {
        if (node == null) {
            return;
        }
        String nodeType = node.path("type").asText("");
        if ("CodeRef".equals(nodeType)) {
            codes.add(CodingInfo.builder()
                    .code(node.path("name").asText(null))
                    .build());
        } else if ("Code".equals(nodeType)) {
            codes.add(CodingInfo.builder()
                    .system(node.path("system").path("name").asText(null))
                    .code(node.path("code").asText(null))
                    .display(node.path("display").asText(null))
                    .build());
        }
    }

    /**
     * Deduplicate retrieves by (resourceType + codeProperty + valueSetRef/codes) and
     * convert to DataRequirementInfo objects.
     */
    private List<DataRequirementInfo> deduplicateRequirements(
            List<RetrieveInfo> retrieves, Map<String, String> valueSetMap) {

        // Use a LinkedHashMap keyed by dedup key to preserve insertion order
        Map<String, DataRequirementInfo> dedupMap = new LinkedHashMap<>();

        for (RetrieveInfo ri : retrieves) {
            String dedupKey = buildDedupKey(ri);
            if (dedupMap.containsKey(dedupKey)) {
                continue;
            }

            DataRequirementInfo.DataRequirementInfoBuilder builder = DataRequirementInfo.builder()
                    .type(ri.resourceType);

            // Code filter
            if (ri.codeProperty != null && (ri.valueSetRefName != null || !ri.directCodes.isEmpty())) {
                CodeFilterInfo.CodeFilterInfoBuilder cfBuilder = CodeFilterInfo.builder()
                        .path(ri.codeProperty);

                if (ri.valueSetRefName != null) {
                    String valueSetId = valueSetMap.get(ri.valueSetRefName);
                    cfBuilder.valueSet(valueSetId != null ? valueSetId : ri.valueSetRefName);
                    cfBuilder.valueSetName(ri.valueSetRefName);
                }

                if (!ri.directCodes.isEmpty()) {
                    cfBuilder.code(ri.directCodes);
                }

                builder.codeFilter(List.of(cfBuilder.build()));
            }

            // Date filter
            if (ri.dateProperty != null) {
                builder.dateFilter(List.of(
                        DateFilterInfo.builder().path(ri.dateProperty).build()
                ));
            }

            dedupMap.put(dedupKey, builder.build());
        }

        return new ArrayList<>(dedupMap.values());
    }

    private String buildDedupKey(RetrieveInfo ri) {
        StringBuilder key = new StringBuilder(ri.resourceType);
        if (ri.codeProperty != null) {
            key.append('|').append(ri.codeProperty);
        }
        if (ri.valueSetRefName != null) {
            key.append('|').append(ri.valueSetRefName);
        }
        for (CodingInfo c : ri.directCodes) {
            key.append('|');
            if (c.getSystem() != null) key.append(c.getSystem());
            key.append(':');
            if (c.getCode() != null) key.append(c.getCode());
        }
        return key.toString();
    }

    /**
     * Intermediate representation of a Retrieve node before deduplication.
     */
    private static class RetrieveInfo {
        String resourceType;
        String codeProperty;
        String dateProperty;
        String valueSetRefName;
        List<CodingInfo> directCodes = new ArrayList<>();
    }
}
