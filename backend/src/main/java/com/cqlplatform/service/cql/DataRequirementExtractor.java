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
 * Handles both inline code filters ([Condition: "Diabetes"]) and
 * bare Retrieve + Where clause patterns ([Condition] C where ...).
 */
@Service
@Slf4j
public class DataRequirementExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FHIR_NS_PREFIX = "{http://hl7.org/fhir}";

    private static final Set<String> DATE_COMPARISON_TYPES = Set.of(
            "Overlaps", "During", "IncludedIn", "In", "Before", "After",
            "SameOrBefore", "SameOrAfter", "Meets", "MeetsAfter", "MeetsBefore",
            "Starts", "Ends"
    );

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

            // Build lookup maps from library-level definitions
            Map<String, String> valueSetMap = buildValueSetMap(root);
            Map<String, String> codeSystemMap = buildCodeSystemMap(root);
            Map<String, CodeDefInfo> codeDefMap = buildCodeDefMap(root);

            // Collect all Retrieve nodes (including Query-enhanced ones)
            List<RetrieveInfo> retrieves = new ArrayList<>();
            collectRetrieves(root, retrieves, codeSystemMap, codeDefMap, new HashSet<>());

            // Convert to DataRequirementInfo and deduplicate
            return deduplicateRequirements(retrieves, valueSetMap);
        } catch (Exception e) {
            log.warn("Failed to extract data requirements from ELM JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Build a mapping from ValueSetDef name to its id (OID or URL).
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
     * Build a mapping from CodeSystemDef name to its id (URL).
     * ELM structure: library.codeSystems.def[] with {name, id} fields.
     */
    private Map<String, String> buildCodeSystemMap(JsonNode root) {
        Map<String, String> map = new HashMap<>();
        JsonNode codeSystems = root.path("library").path("codeSystems").path("def");
        if (codeSystems.isArray()) {
            for (JsonNode cs : codeSystems) {
                String name = cs.path("name").asText(null);
                String id = cs.path("id").asText(null);
                if (name != null && id != null) {
                    map.put(name, id);
                }
            }
        }
        return map;
    }

    /**
     * Build a mapping from code definition name to its code system info.
     * ELM structure: library.codes.def[] with {name, id, codeSystem: {name}} fields.
     */
    private Map<String, CodeDefInfo> buildCodeDefMap(JsonNode root) {
        Map<String, CodeDefInfo> map = new HashMap<>();
        JsonNode codes = root.path("library").path("codes").path("def");
        if (codes.isArray()) {
            for (JsonNode cd : codes) {
                String name = cd.path("name").asText(null);
                String codeSystemName = cd.path("codeSystem").path("name").asText(null);
                if (name != null && codeSystemName != null) {
                    CodeDefInfo info = new CodeDefInfo();
                    info.codeSystemName = codeSystemName;
                    info.code = cd.path("id").asText(null);
                    info.display = cd.path("display").asText(null);
                    map.put(name, info);
                }
            }
        }
        return map;
    }

    /**
     * Recursively walk the ELM JSON tree and collect Retrieve nodes.
     * When a Query node is found with a bare Retrieve source, analyze the Where clause.
     * Tracks visited nodes by identity to avoid processing the same Retrieve twice.
     */
    private void collectRetrieves(JsonNode node, List<RetrieveInfo> retrieves,
                                  Map<String, String> codeSystemMap, Map<String, CodeDefInfo> codeDefMap,
                                  Set<JsonNode> handledRetrieves) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            String type = node.path("type").asText(null);

            if ("Query".equals(type)) {
                // Handle Query nodes specially: extract Retrieve from source and enhance from Where
                handleQuery(node, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                // Still recurse into non-source children (e.g. let clauses may contain nested queries)
                // but skip source to avoid double-counting
                JsonNode where = node.get("where");
                if (where != null) {
                    collectRetrieves(where, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                }
                JsonNode let = node.get("let");
                if (let != null) {
                    collectRetrieves(let, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                }
                JsonNode relationship = node.get("relationship");
                if (relationship != null) {
                    collectRetrieves(relationship, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                }
                JsonNode ret = node.get("return");
                if (ret != null) {
                    collectRetrieves(ret, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                }
                return;
            }

            if ("Retrieve".equals(type) && !handledRetrieves.contains(node)) {
                RetrieveInfo info = parseRetrieve(node, codeDefMap, codeSystemMap);
                if (info != null) {
                    retrieves.add(info);
                    handledRetrieves.add(node);
                }
            }

            // Generic recursion for all other node types
            if (!"Query".equals(type)) {
                for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                    collectRetrieves(it.next(), retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectRetrieves(child, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
            }
        }
    }

    /**
     * Handle a Query node: extract Retrieve from source[0] and enhance from Where clause.
     */
    private void handleQuery(JsonNode queryNode, List<RetrieveInfo> retrieves,
                             Map<String, String> codeSystemMap, Map<String, CodeDefInfo> codeDefMap,
                             Set<JsonNode> handledRetrieves) {
        JsonNode sources = queryNode.get("source");
        if (sources == null || !sources.isArray() || sources.isEmpty()) {
            return;
        }

        // Process each source in the Query
        for (JsonNode source : sources) {
            JsonNode sourceExpr = source.get("expression");
            if (sourceExpr == null) {
                continue;
            }

            String sourceType = sourceExpr.path("type").asText(null);
            if (!"Retrieve".equals(sourceType)) {
                // Recurse into non-Retrieve sources (e.g., function calls, other queries)
                collectRetrieves(sourceExpr, retrieves, codeSystemMap, codeDefMap, handledRetrieves);
                continue;
            }

            // Already handled
            if (handledRetrieves.contains(sourceExpr)) {
                continue;
            }

            RetrieveInfo info = parseRetrieve(sourceExpr, codeDefMap, codeSystemMap);
            if (info == null) {
                continue;
            }
            handledRetrieves.add(sourceExpr);

            String alias = source.path("alias").asText(null);

            // If the Retrieve has no inline code filter, try to extract from Where clause
            boolean hasInlineCodes = (info.valueSetRefName != null || !info.directCodes.isEmpty());
            JsonNode where = queryNode.get("where");
            if (where != null && alias != null) {
                enhanceFromWhere(where, info, alias, codeSystemMap, codeDefMap, hasInlineCodes);
            }

            retrieves.add(info);
        }
    }

    /**
     * Analyze the Where clause of a Query to extract additional filter information
     * for a Retrieve that has no inline code filter.
     */
    private void enhanceFromWhere(JsonNode where, RetrieveInfo info, String alias,
                                  Map<String, String> codeSystemMap, Map<String, CodeDefInfo> codeDefMap,
                                  boolean hasInlineCodes) {
        walkWhereClause(where, info, alias, codeSystemMap, codeDefMap, hasInlineCodes);
    }

    /**
     * Recursive AST walker for Where clause patterns.
     */
    private void walkWhereClause(JsonNode node, RetrieveInfo info, String alias,
                                 Map<String, String> codeSystemMap, Map<String, CodeDefInfo> codeDefMap,
                                 boolean hasInlineCodes) {
        if (node == null || !node.isObject()) {
            return;
        }

        String type = node.path("type").asText("");

        switch (type) {
            case "And":
            case "Or":
                // Recurse into both operands
                JsonNode andOps = node.get("operand");
                if (andOps != null && andOps.isArray()) {
                    for (JsonNode op : andOps) {
                        walkWhereClause(op, info, alias, codeSystemMap, codeDefMap, hasInlineCodes);
                    }
                }
                break;

            case "Not":
                JsonNode notOp = node.get("operand");
                if (notOp != null) {
                    walkWhereClause(notOp, info, alias, codeSystemMap, codeDefMap, hasInlineCodes);
                }
                break;

            case "Exists":
                // exists(C.code.coding Y where Y.system = "url" ...) pattern
                JsonNode existsOp = node.get("operand");
                if (existsOp != null && !hasInlineCodes) {
                    handleExistsPattern(existsOp, info, alias, codeSystemMap);
                }
                break;

            case "InValueSet":
                // C.code in "ValueSetName" pattern
                if (!hasInlineCodes) {
                    handleInValueSetPattern(node, info, alias);
                }
                break;

            case "Equal":
            case "Equivalent":
                // Handle direct property ~ CodeRef patterns (e.g., E.class ~ "AMB")
                if (!hasInlineCodes) {
                    handleCodeRefComparison(node, info, alias, codeSystemMap, codeDefMap);
                }
                // Also check for date comparison patterns within Equal/Equivalent
                if (DATE_COMPARISON_TYPES.contains(type)) {
                    handleDateComparisonPattern(node, info, alias);
                }
                break;

            default:
                // Check for date comparison patterns
                if (DATE_COMPARISON_TYPES.contains(type)) {
                    handleDateComparisonPattern(node, info, alias);
                }
                break;
        }
    }

    /**
     * Handle exists(...) pattern to extract code system info.
     * Looks for: Exists → Query(source: Property chain) → Where(Equal/Equivalent(.system, Literal))
     */
    private void handleExistsPattern(JsonNode existsOperand, RetrieveInfo info, String alias,
                                     Map<String, String> codeSystemMap) {
        String opType = existsOperand.path("type").asText("");

        if ("Query".equals(opType)) {
            // Inner query: source is typically a property chain like C.code.coding
            JsonNode innerSources = existsOperand.get("source");
            if (innerSources != null && innerSources.isArray() && !innerSources.isEmpty()) {
                JsonNode innerSourceExpr = innerSources.get(0).get("expression");
                String codePath = extractCodePathFromPropertyChain(innerSourceExpr, alias);
                if (codePath != null && info.codeProperty == null) {
                    info.codeProperty = codePath;
                }
            }

            // Analyze inner Where for system = "url" pattern
            JsonNode innerWhere = existsOperand.get("where");
            if (innerWhere != null) {
                String innerAlias = innerSources != null && innerSources.isArray() && !innerSources.isEmpty()
                        ? innerSources.get(0).path("alias").asText(null) : null;
                extractCodeSystemFromInnerWhere(innerWhere, info, innerAlias, codeSystemMap);
            }
        } else if ("Filter".equals(opType)) {
            // Filter node: source + condition
            JsonNode filterSource = existsOperand.get("source");
            String codePath = extractCodePathFromPropertyChain(filterSource, alias);
            if (codePath != null && info.codeProperty == null) {
                info.codeProperty = codePath;
            }
            JsonNode condition = existsOperand.get("condition");
            if (condition != null) {
                extractCodeSystemFromInnerWhere(condition, info, null, codeSystemMap);
            }
        }
    }

    /**
     * Extract the code path from a property chain like C.code.coding -> "code".
     * Returns the first property after the alias (e.g., "code" from C.code.coding).
     */
    private String extractCodePathFromPropertyChain(JsonNode node, String alias) {
        if (node == null || !node.isObject()) {
            return null;
        }

        String type = node.path("type").asText("");
        if (!"Property".equals(type)) {
            return null;
        }

        // Build the property chain from inside out, tracking the innermost Property node
        List<String> chain = new ArrayList<>();
        JsonNode current = node;
        JsonNode lastProperty = null;
        while (current != null && "Property".equals(current.path("type").asText(""))) {
            lastProperty = current;
            chain.add(0, current.path("path").asText(""));
            current = current.get("source");
        }

        // Check if the innermost source references our alias via AliasRef
        if (current != null) {
            String refType = current.path("type").asText("");
            String refName = current.path("name").asText("");
            if ("AliasRef".equals(refType) && alias.equals(refName) && !chain.isEmpty()) {
                return chain.get(0); // Return first property (e.g., "code" from code.coding)
            }
        }

        // Alternative: scope-based property (no explicit source, uses scope attribute)
        // The scope can be on the outermost or innermost Property node depending on the chain
        if (current == null && !chain.isEmpty()) {
            // Check outermost node's scope
            String scope = node.path("scope").asText(null);
            if (scope != null && scope.equals(alias)) {
                return chain.get(0);
            }
            // Check innermost Property's scope (e.g., M.medication has scope="M", coding wraps it)
            if (lastProperty != null) {
                scope = lastProperty.path("scope").asText(null);
                if (scope != null && scope.equals(alias)) {
                    return chain.get(0);
                }
            }
        }

        return null;
    }

    /**
     * Extract code system URL from inner Where clause of exists() pattern.
     * Looks for Equal/Equivalent comparing .system to a string literal or CodeSystemRef.
     */
    private void extractCodeSystemFromInnerWhere(JsonNode node, RetrieveInfo info,
                                                  String innerAlias, Map<String, String> codeSystemMap) {
        if (node == null || !node.isObject()) {
            return;
        }

        String type = node.path("type").asText("");

        if ("And".equals(type) || "Or".equals(type)) {
            JsonNode ops = node.get("operand");
            if (ops != null && ops.isArray()) {
                for (JsonNode op : ops) {
                    extractCodeSystemFromInnerWhere(op, info, innerAlias, codeSystemMap);
                }
            }
            return;
        }

        if ("Equal".equals(type) || "Equivalent".equals(type)) {
            JsonNode ops = node.get("operand");
            if (ops == null || !ops.isArray() || ops.size() < 2) {
                return;
            }

            // Look for pattern: Property(.system) = Literal("url")
            JsonNode left = ops.get(0);
            JsonNode right = ops.get(1);

            String systemUrl = extractSystemComparison(left, right, innerAlias);
            if (systemUrl == null) {
                systemUrl = extractSystemComparison(right, left, innerAlias);
            }

            if (systemUrl != null && info.codeSystemUrl == null) {
                info.codeSystemUrl = systemUrl;
                // Try to find the code system name from the map
                for (Map.Entry<String, String> entry : codeSystemMap.entrySet()) {
                    if (entry.getValue().equals(systemUrl)) {
                        info.codeSystemName = entry.getKey();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Check if left is a Property accessing .system and right is a Literal string URL.
     * The Property may be wrapped in a FunctionRef (e.g., FHIRHelpers.ToString(Coding.system))
     * due to the CQL-to-ELM translator converting FHIR uri types.
     */
    private String extractSystemComparison(JsonNode propertyNode, JsonNode literalNode, String innerAlias) {
        if (propertyNode == null || literalNode == null) {
            return null;
        }

        // Unwrap FunctionRef (e.g., FHIRHelpers.ToString) to reach the inner Property
        JsonNode unwrapped = unwrapToProperty(propertyNode);
        if (unwrapped == null) {
            return null;
        }

        // Check unwrapped is a Property with path "system" (or "url")
        String path = unwrapped.path("path").asText("");
        if (!"system".equals(path) && !"url".equals(path)) {
            return null;
        }

        // Check right is a Literal string
        String litType = literalNode.path("type").asText("");
        if ("Literal".equals(litType)) {
            String valueType = literalNode.path("valueType").asText("");
            if (valueType.contains("String") || valueType.isEmpty()) {
                return literalNode.path("value").asText(null);
            }
        }

        return null;
    }

    /**
     * Unwrap FunctionRef/As/Convert nodes to reach the underlying Property node.
     * CQL-to-ELM translator wraps FHIR primitive types (uri, code, etc.) in
     * FHIRHelpers.ToString/ToCode, producing: FunctionRef → Property instead of bare Property.
     */
    private JsonNode unwrapToProperty(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("type").asText("");
        if ("Property".equals(type)) {
            return node;
        }
        if ("FunctionRef".equals(type)) {
            JsonNode ops = node.get("operand");
            if (ops != null && ops.isArray() && !ops.isEmpty()) {
                return unwrapToProperty(ops.get(0));
            }
        }
        if ("As".equals(type) || "Convert".equals(type)) {
            JsonNode operand = node.get("operand");
            return unwrapToProperty(operand);
        }
        return null;
    }

    /**
     * Unwrap ToConcept/FunctionRef/As nodes to reach an underlying CodeRef node.
     * CQL-to-ELM translator wraps CodeRef in ToConcept when comparing CodeableConcept ~ Code,
     * producing: ToConcept(CodeRef) or FunctionRef("ToConcept", CodeRef) instead of bare CodeRef.
     */
    private JsonNode unwrapToCodeRef(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("type").asText("");
        if ("CodeRef".equals(type)) {
            return node;
        }
        if ("ToConcept".equals(type) || "ToCode".equals(type)) {
            // ToConcept has a single "operand" field (not array)
            JsonNode operand = node.get("operand");
            if (operand != null) {
                return unwrapToCodeRef(operand);
            }
        }
        if ("FunctionRef".equals(type)) {
            JsonNode ops = node.get("operand");
            if (ops != null && ops.isArray() && !ops.isEmpty()) {
                return unwrapToCodeRef(ops.get(0));
            }
        }
        if ("As".equals(type) || "Convert".equals(type)) {
            return unwrapToCodeRef(node.get("operand"));
        }
        return null;
    }

    /**
     * Handle InValueSet pattern: operand[0] is a property, operand[1] or "valueset" is a ValueSetRef.
     */
    private void handleInValueSetPattern(JsonNode node, RetrieveInfo info, String alias) {
        // InValueSet has "code" (the operand) and "valueset" (ValueSetRef name)
        JsonNode codeOp = node.get("code");
        JsonNode valueSetNode = node.get("valueset");

        if (codeOp != null && valueSetNode != null) {
            String codePath = extractPropertyPath(codeOp, alias);
            String vsName = valueSetNode.path("name").asText(null);

            if (codePath != null && vsName != null) {
                if (info.codeProperty == null) {
                    info.codeProperty = codePath;
                }
                if (info.valueSetRefName == null) {
                    info.valueSetRefName = vsName;
                }
            }
        }
    }

    /**
     * Handle Equal/Equivalent with CodeRef pattern.
     * Detects: E.class ~ "AMB" which in ELM becomes:
     *   Equivalent(Property(source=AliasRef("E"), path="class"), CodeRef(name="AMB"))
     * The Property side may be wrapped in FunctionRef (e.g., FHIRHelpers.ToCode).
     */
    private void handleCodeRefComparison(JsonNode node, RetrieveInfo info, String alias,
                                          Map<String, String> codeSystemMap, Map<String, CodeDefInfo> codeDefMap) {
        JsonNode ops = node.get("operand");
        if (ops == null || !ops.isArray() || ops.size() < 2) {
            return;
        }

        JsonNode left = ops.get(0);
        JsonNode right = ops.get(1);

        // Try both orderings: (Property, CodeRef) and (CodeRef, Property)
        if (!tryExtractCodeRefFilter(left, right, info, alias, codeSystemMap, codeDefMap)) {
            tryExtractCodeRefFilter(right, left, info, alias, codeSystemMap, codeDefMap);
        }
    }

    /**
     * Try to extract a code filter from a (property, codeRef) pair.
     * Returns true if a CodeRef pattern was found and processed.
     */
    private boolean tryExtractCodeRefFilter(JsonNode propertyNode, JsonNode codeRefNode,
                                             RetrieveInfo info, String alias,
                                             Map<String, String> codeSystemMap,
                                             Map<String, CodeDefInfo> codeDefMap) {
        if (codeRefNode == null) {
            return false;
        }

        // Check if codeRefNode is a CodeRef (may be wrapped in ToConcept/FunctionRef)
        JsonNode actualCodeRef = unwrapToCodeRef(codeRefNode);
        if (actualCodeRef == null) {
            return false;
        }

        String codeRefName = actualCodeRef.path("name").asText(null);
        if (codeRefName == null) {
            return false;
        }

        // Extract property path from the property side (may be wrapped in FunctionRef)
        JsonNode unwrapped = unwrapToProperty(propertyNode);
        String propPath = null;
        if (unwrapped != null) {
            propPath = extractPropertyPath(unwrapped, alias);
        }

        if (propPath == null) {
            return false;
        }

        // Look up the CodeRef in codeDefMap to get code system info
        CodeDefInfo codeDef = codeDefMap.get(codeRefName);
        if (codeDef != null) {
            if (info.codeProperty == null) {
                info.codeProperty = propPath;
            }
            String resolvedSystemUrl = codeSystemMap.get(codeDef.codeSystemName);
            if (resolvedSystemUrl != null && info.codeSystemUrl == null) {
                info.codeSystemUrl = resolvedSystemUrl;
                info.codeSystemName = codeDef.codeSystemName;
            }
            // Add the code as a direct code reference
            info.directCodes.add(CodingInfo.builder()
                    .system(resolvedSystemUrl)
                    .code(codeDef.code)
                    .display(codeDef.display)
                    .build());
        }

        return true;
    }

    /**
     * Handle date comparison patterns (Overlaps, During, IncludedIn, etc.)
     * where one operand is a property of the alias and the other references "Measurement Period".
     */
    private void handleDateComparisonPattern(JsonNode node, RetrieveInfo info, String alias) {
        JsonNode ops = node.get("operand");
        if (ops == null || !ops.isArray() || ops.size() < 2) {
            return;
        }

        JsonNode left = ops.get(0);
        JsonNode right = ops.get(1);

        // Try both orderings: (property, parameterRef) and (parameterRef, property)
        String datePath = tryExtractDateFilter(left, right, alias);
        if (datePath == null) {
            datePath = tryExtractDateFilter(right, left, alias);
        }

        if (datePath != null && info.dateProperty == null) {
            info.dateProperty = datePath;
        }
    }

    /**
     * Try to extract a date filter path from a (property, parameterRef) pair.
     * The property side may be wrapped in FunctionRef (e.g., NormalizeInterval).
     */
    private String tryExtractDateFilter(JsonNode propertyNode, JsonNode paramNode, String alias) {
        if (!isParameterRef(paramNode)) {
            return null;
        }

        // Direct property: E.period
        String path = extractPropertyPath(propertyNode, alias);
        if (path != null) {
            return path;
        }

        // Unwrap FunctionRef/As/Convert/Case to find the underlying property
        path = extractDatePropertyFromExpression(propertyNode, alias);
        return path;
    }

    /**
     * Extract a date property path from a potentially wrapped expression.
     * Handles: FunctionRef(Property), As(Property), Case(... Property ...),
     * and combinations thereof.
     * FHIR choice types like effective[x] produce Case expressions in ELM:
     *   Case(when Is(dateTime) then ToDateTime(As(Property("effective"))),
     *        when Is(instant) then ToDateTime(As(Property("effective"))))
     */
    private String extractDatePropertyFromExpression(JsonNode node, String alias) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("type").asText("");

        // FunctionRef wrapping: NormalizeInterval(P.performed) or ToDateTime(As(Property))
        if ("FunctionRef".equals(type)) {
            JsonNode fnOps = node.get("operand");
            if (fnOps != null && fnOps.isArray()) {
                for (JsonNode fnOp : fnOps) {
                    String path = extractPropertyPath(fnOp, alias);
                    if (path != null) return path;
                    // Recurse for nested wrappers
                    path = extractDatePropertyFromExpression(fnOp, alias);
                    if (path != null) return path;
                }
            }
        }

        // As/Convert wrapping
        if ("As".equals(type) || "Convert".equals(type)) {
            JsonNode operand = node.get("operand");
            String path = extractPropertyPath(operand, alias);
            if (path != null) return path;
            return extractDatePropertyFromExpression(operand, alias);
        }

        // Case expression from FHIR choice types (effective[x], onset[x], etc.)
        if ("Case".equals(type)) {
            JsonNode caseItems = node.get("caseItem");
            if (caseItems != null && caseItems.isArray()) {
                for (JsonNode caseItem : caseItems) {
                    JsonNode then = caseItem.get("then");
                    if (then != null) {
                        String path = extractPropertyPath(then, alias);
                        if (path != null) return path;
                        path = extractDatePropertyFromExpression(then, alias);
                        if (path != null) return path;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if a node is a ParameterRef (typically "Measurement Period").
     */
    private boolean isParameterRef(JsonNode node) {
        if (node == null) {
            return false;
        }
        String type = node.path("type").asText("");
        return "ParameterRef".equals(type);
    }

    /**
     * Extract a simple property path from a Property node referencing the given alias.
     * E.g., Property(source=AliasRef("E"), path="period") -> "period"
     */
    private String extractPropertyPath(JsonNode node, String alias) {
        if (node == null || !node.isObject()) {
            return null;
        }

        String type = node.path("type").asText("");
        if (!"Property".equals(type)) {
            return null;
        }

        String path = node.path("path").asText(null);
        if (path == null) {
            return null;
        }

        // Check source is AliasRef with matching alias
        JsonNode source = node.get("source");
        if (source != null) {
            String sourceType = source.path("type").asText("");
            String sourceName = source.path("name").asText("");
            if ("AliasRef".equals(sourceType) && alias.equals(sourceName)) {
                return path;
            }
        }

        // Check scope-based property
        String scope = node.path("scope").asText(null);
        if (scope != null && scope.equals(alias)) {
            return path;
        }

        return null;
    }

    /**
     * Parse a single Retrieve node into an intermediate RetrieveInfo object.
     */
    // PAT-116: signature extended to accept codeDefMap + codeSystemMap so
    // extractCodeRef can resolve a CQL CodeRef (just a name like
    // "Hemoglobin A1c") into the actual {code="17856-6", system="http://loinc.org"}
    // that downstream FHIR clients need. Before PAT-116 the ref name was
    // stored as if it were the literal code, producing unusable data requirements.
    private RetrieveInfo parseRetrieve(JsonNode retrieveNode,
                                       Map<String, CodeDefInfo> codeDefMap,
                                       Map<String, String> codeSystemMap) {
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
                    JsonNode operand = codesNode.get("operand");
                    if (operand != null) {
                        extractCodeRef(operand, directCodes, codeDefMap, codeSystemMap);
                    }
                    break;
                case "List":
                    JsonNode elements = codesNode.get("element");
                    if (elements != null && elements.isArray()) {
                        for (JsonNode elem : elements) {
                            extractCodeRef(elem, directCodes, codeDefMap, codeSystemMap);
                        }
                    }
                    break;
                default:
                    extractCodeRef(codesNode, directCodes, codeDefMap, codeSystemMap);
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
     *
     * <p>PAT-116: CodeRef names are resolved against {@code codeDefMap} (built
     * from {@code library.codes.def}) and {@code codeSystemMap} (built from
     * {@code library.codeSystems.def}) to recover the real (code, system)
     * pair. Downstream consumers treat the output as "what to ask a FHIR
     * server for" — the ref name alone (e.g. "Hemoglobin A1c") cannot drive
     * a real FHIR query; only the resolved code "17856-6" + system URL can.
     *
     * <p>Unresolvable refs (ref name not in {@code codeDefMap}) fall back to
     * storing the raw name as the code — matches legacy behavior and is the
     * best guess for external-library refs whose def lives in another ELM
     * we cannot see.
     */
    private void extractCodeRef(JsonNode node, List<CodingInfo> codes,
                                Map<String, CodeDefInfo> codeDefMap,
                                Map<String, String> codeSystemMap) {
        if (node == null) {
            return;
        }
        String nodeType = node.path("type").asText("");
        if ("CodeRef".equals(nodeType)) {
            String refName = node.path("name").asText(null);
            CodeDefInfo def = refName != null ? codeDefMap.get(refName) : null;
            CodingInfo.CodingInfoBuilder b = CodingInfo.builder();
            if (def != null) {
                b.code(def.code);
                b.display(def.display);
                if (def.codeSystemName != null) {
                    String systemUrl = codeSystemMap.get(def.codeSystemName);
                    b.system(systemUrl != null ? systemUrl : def.codeSystemName);
                }
            } else {
                // Fallback: keep the ref name as code — covers external-library
                // refs whose CodeDef lives outside the current ELM. Legacy behavior.
                b.code(refName);
            }
            codes.add(b.build());
        } else if ("Code".equals(nodeType)) {
            // Code literal has `system: CodeSystemRef{ name }`; resolve to URL.
            String systemName = node.path("system").path("name").asText(null);
            String systemUrl = systemName != null ? codeSystemMap.get(systemName) : null;
            codes.add(CodingInfo.builder()
                    .system(systemUrl != null ? systemUrl : systemName)
                    .code(node.path("code").asText(null))
                    .display(node.path("display").asText(null))
                    .build());
        }
    }

    /**
     * Deduplicate retrieves by (resourceType + codeProperty + valueSetRef/codes + codeSystemUrl)
     * and convert to DataRequirementInfo objects.
     */
    private List<DataRequirementInfo> deduplicateRequirements(
            List<RetrieveInfo> retrieves, Map<String, String> valueSetMap) {

        Map<String, DataRequirementInfo> dedupMap = new LinkedHashMap<>();

        for (RetrieveInfo ri : retrieves) {
            String dedupKey = buildDedupKey(ri);
            if (dedupMap.containsKey(dedupKey)) {
                continue;
            }

            DataRequirementInfo.DataRequirementInfoBuilder builder = DataRequirementInfo.builder()
                    .type(ri.resourceType);

            // Code filter from inline codes or Where clause code system
            boolean hasCodeFilter = ri.codeProperty != null
                    && (ri.valueSetRefName != null || !ri.directCodes.isEmpty() || ri.codeSystemUrl != null);
            if (hasCodeFilter) {
                CodeFilterInfo.CodeFilterInfoBuilder cfBuilder = CodeFilterInfo.builder()
                        .path(ri.codeProperty);

                if (ri.valueSetRefName != null) {
                    String valueSetId = valueSetMap.get(ri.valueSetRefName);
                    cfBuilder.valueSet(valueSetId != null ? valueSetId : ri.valueSetRefName);
                    cfBuilder.valueSetName(ri.valueSetRefName);
                }

                if (ri.codeSystemUrl != null) {
                    cfBuilder.codeSystemUrl(ri.codeSystemUrl);
                    cfBuilder.codeSystemName(ri.codeSystemName);
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
        if (ri.codeSystemUrl != null) {
            key.append("|cs:").append(ri.codeSystemUrl);
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
        String codeSystemUrl;
        String codeSystemName;
        List<CodingInfo> directCodes = new ArrayList<>();
    }

    /**
     * Intermediate representation of a code definition from library.codes.def[].
     * Maps a named code (e.g., "AMB") to its code system and value.
     */
    private static class CodeDefInfo {
        String codeSystemName;  // e.g., "ActCode"
        String code;            // e.g., "AMB"
        String display;         // e.g., "ambulatory"
    }
}
