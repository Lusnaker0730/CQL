package com.cqlplatform.service.ecqm;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.ecqm.EcqmArtifactRequest;
import com.cqlplatform.model.ecqm.EcqmConstants;
import com.cqlplatform.service.authoring.CustomModifierBuildException;
import com.cqlplatform.service.authoring.CustomModifierCqlBuilder;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.authoring.TemplateService;
import com.cqlplatform.validation.ModifierValueValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

/**
 * Validates eCQM artifact expression tree data for safety and structural consistency.
 *
 * <p>Security: Instead of fragile regex-based XSS detection, we use Spring's
 * {@link HtmlUtils#htmlEscape} to detect embedded HTML. If a string value differs
 * after HTML-escaping, it contains HTML markup and is rejected. This is encoding-safe
 * and not bypassable via obfuscation tricks that defeat regex patterns.
 *
 * <p>Structural: Validates element types against registered templates and modifier IDs
 * against registered modifiers, matching the CDS authoring {@code ExpressionTreeValidator}.
 * Also validates eCQM-specific constraints (scoring type populations, define name uniqueness).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EcqmExpressionTreeValidator {

    private final TemplateService templateService;
    private final ModifierService modifierService;
    private final CustomModifierCqlBuilder customModifierCqlBuilder;

    /** Max allowed nesting depth to prevent stack overflow on deeply nested payloads. */
    private static final int MAX_DEPTH = 50;

    /** Max total nodes to traverse to prevent excessive processing time. */
    private static final int MAX_NODES = 10_000;

    public void validate(EcqmArtifactRequest request) {
        List<String> errors = new ArrayList<>();
        int[] nodeCount = {0};

        // ── XSS + structural validation on expression trees ──────────────
        validateTrees("populationGroups", request.getPopulationGroups(), errors, nodeCount);
        validateTrees("baseElements", request.getBaseElements(), errors, nodeCount);
        validateTrees("parameters", request.getParameters(), errors, nodeCount);
        validateTrees("supplementalData", request.getSupplementalData(), errors, nodeCount);
        validateTrees("stratifiers", request.getStratifiers(), errors, nodeCount);

        // ── eCQM-specific structural validation ──────────────────────────
        validateDefineNameUniqueness(request, errors);
        validateAggregateMethods(request, errors);

        if (!errors.isEmpty()) {
            log.warn("eCQM expression tree validation errors: {}", errors);
            throw new ValidationException("Invalid eCQM artifact data", errors);
        }
    }

    /**
     * Rejects unknown {@code aggregateMethod} values on CV / ratio observations before
     * the measure reaches the database. Accepts canonical forms + aliases
     * ({@code Min}, {@code Max}, {@code Avg}, {@code Mean}) via
     * {@link com.cqlplatform.service.measure.MeasureScoreCalculator#normalizeAggregateMethod}.
     *
     * <p>Rationale: #PAT-088 added runtime handling where unknown aggregateMethod
     * yields null score + a log warning. That's the right behavior at evaluation time
     * (other measures in the request shouldn't suffer from one typo), but it's far
     * better to catch the typo at save time so the author sees an immediate error and
     * can fix it before anyone ever runs the measure. Saved measures with null
     * aggregateMethod silently run with "average" semantics (preserving the existing
     * "no aggregate specified" default); saved measures with a VALUE for
     * aggregateMethod that's NOT one of the known canonicals/aliases are user error.
     */
    @SuppressWarnings("unchecked")
    private void validateAggregateMethods(EcqmArtifactRequest request, List<String> errors) {
        List<Map<String, Object>> groups = request.getPopulationGroups();
        if (groups == null) return;
        for (int gi = 0; gi < groups.size(); gi++) {
            Map<String, Object> group = groups.get(gi);
            if (group == null) continue;
            Object obsObj = group.get("observations");
            if (!(obsObj instanceof List<?> observations)) continue;
            for (int oi = 0; oi < observations.size(); oi++) {
                Object item = observations.get(oi);
                if (!(item instanceof Map<?, ?> rawMap)) continue;
                Map<String, Object> obs = (Map<String, Object>) rawMap;
                Object rawMethod = obs.get("aggregateMethod");
                // A null / missing aggregateMethod is acceptable — preserves the
                // "no aggregate specified → average" semantic. Only non-null VALUES
                // that fail normalization are user errors.
                if (!(rawMethod instanceof String str) || str.isBlank()) continue;
                if (com.cqlplatform.service.measure.MeasureScoreCalculator.normalizeAggregateMethod(str) == null) {
                    errors.add(String.format(
                            "populationGroups[%d].observations[%d].aggregateMethod: '%s' is not a recognized aggregate method. "
                                    + "Supported: count, sum, average, median, minimum, maximum (aliases: avg, mean, min, max).",
                            gi, oi, str));
                }
            }
        }
    }

    // ── Tree traversal ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void validateTrees(String fieldName, List<Map<String, Object>> trees,
                               List<String> errors, int[] nodeCount) {
        if (trees == null) return;
        for (int i = 0; i < trees.size(); i++) {
            validateNode(fieldName + "[" + i + "]", trees.get(i), errors, 0, nodeCount);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(String path, Map<String, Object> node,
                              List<String> errors, int depth, int[] nodeCount) {
        if (node == null) return;
        if (depth > MAX_DEPTH) {
            errors.add(String.format("%s: expression tree exceeds maximum nesting depth (%d)", path, MAX_DEPTH));
            return;
        }
        if (++nodeCount[0] > MAX_NODES) {
            if (nodeCount[0] == MAX_NODES + 1) {
                errors.add(String.format("Expression tree exceeds maximum node count (%d)", MAX_NODES));
            }
            return;
        }

        // Check all string values for HTML content (XSS)
        // Skip "fields" — these are form field definitions (type: string/number/textarea/valueset),
        // not expression tree nodes. Recursing into them causes false "unknown element type" errors.
        // Skip "values" — these are modifier.values fields constrained by
        // ModifierValueValidator (whitelist + regex; tighter than HTML escape).
        // The HTML check would falsely flag legitimate operators like ">=" / "<=".
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if ("fields".equals(entry.getKey())) continue;
            if ("values".equals(entry.getKey())) continue;
            if (entry.getValue() instanceof String strVal) {
                checkHtmlContent(path + "." + entry.getKey(), strVal, errors);
            } else if (entry.getValue() instanceof Map) {
                validateNode(path + "." + entry.getKey(), (Map<String, Object>) entry.getValue(),
                        errors, depth + 1, nodeCount);
            } else if (entry.getValue() instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        validateNode(path + "." + entry.getKey() + "[" + i + "]",
                                (Map<String, Object>) item, errors, depth + 1, nodeCount);
                    } else if (item instanceof String strItem) {
                        checkHtmlContent(path + "." + entry.getKey() + "[" + i + "]", strItem, errors);
                    }
                }
            }
        }

        // ── Structural: element type validation ──────────────────────────
        String type = toStr(node.get("type"));
        if (type != null && !type.isBlank() && !templateService.isValidElementType(type)) {
            String name = toStr(node.get("name"));
            errors.add(String.format("%s: unknown element type '%s'%s",
                    path, type, name != null ? " (name: " + name + ")" : ""));
        }

        // ── Structural: modifier ID + values validation ─────────────────
        Object modifiersObj = node.get("modifiers");
        if (modifiersObj instanceof List<?> modList) {
            for (Object modObj : modList) {
                if (modObj instanceof Map<?, ?> modMap) {
                    String modId = toStr(modMap.get("id"));
                    if (modId != null && modId.startsWith("custom_")) {
                        // Custom modifier — validate structured rules tree (CqlInjection sink
                        // when the client-supplied cqlTemplate string was trusted). Backend
                        // rebuilds CQL from values.rules; this dry-run rejects malformed payloads.
                        Object valuesObj = modMap.get("values");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> values = valuesObj instanceof Map ? (Map<String, Object>) valuesObj : null;
                        try {
                            customModifierCqlBuilder.validate(values);
                        } catch (CustomModifierBuildException ex) {
                            errors.add(String.format("%s: custom modifier '%s' invalid — %s",
                                    path, modId, ex.getMessage()));
                        }
                    } else if (modId != null && !modId.isBlank() && !modifierService.isValidModifierId(modId)) {
                        String modName = toStr(modMap.get("name"));
                        errors.add(String.format("%s: unknown modifier id '%s'%s",
                                path, modId, modName != null ? " (name: " + modName + ")" : ""));
                    }
                    // Defense-in-depth whitelist of modifier.values fields that flow
                    // into ExpressionCqlEngine.applyModifier and generated CQL.
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mod = (Map<String, Object>) modMap;
                    ModifierValueValidator.validate(mod, path, errors);
                }
            }
        }

        // ── Recurse into conjunction childInstances ──────────────────────
        Boolean conjunction = toBoolean(node.get("conjunction"));
        if (Boolean.TRUE.equals(conjunction)) {
            Object children = node.get("childInstances");
            if (children instanceof List<?> childList) {
                for (int i = 0; i < childList.size(); i++) {
                    Object child = childList.get(i);
                    if (child instanceof Map) {
                        validateNode(path + ".childInstances[" + i + "]",
                                (Map<String, Object>) child, errors, depth + 1, nodeCount);
                    }
                }
            }
        }
    }

    // ── XSS detection via HTML escaping ──────────────────────────────────

    /**
     * Detects HTML/script content by comparing the original string with its HTML-escaped version.
     * If they differ, the string contains HTML markup (angle brackets, entities, etc.).
     * This approach is encoding-safe and cannot be bypassed by obfuscation tricks.
     */
    private void checkHtmlContent(String fieldPath, String value, List<String> errors) {
        if (value == null || value.isEmpty()) return;

        // Quick pre-check: if no angle brackets or ampersands, skip the expensive escape
        if (value.indexOf('<') < 0 && value.indexOf('>') < 0 && value.indexOf('&') < 0) {
            // Still check for javascript: and other URI schemes
            String lower = value.toLowerCase();
            if (lower.contains("javascript:") || lower.contains("vbscript:")
                    || lower.contains("data:text/html")) {
                errors.add(String.format("Potentially unsafe content detected in field '%s'", fieldPath));
            }
            return;
        }

        String escaped = HtmlUtils.htmlEscape(value);
        if (!escaped.equals(value)) {
            errors.add(String.format("HTML content not allowed in field '%s'", fieldPath));
        }
    }

    // ── eCQM define-name uniqueness ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void validateDefineNameUniqueness(EcqmArtifactRequest request, List<String> errors) {
        Set<String> allNames = new LinkedHashSet<>();

        // Base element names
        if (request.getBaseElements() != null) {
            for (Map<String, Object> be : request.getBaseElements()) {
                String name = trimOrNull(toStr(be.get("name")));
                if (name != null && !allNames.add(name)) {
                    errors.add(String.format("Duplicate define name '%s' in base elements", name));
                }
            }
        }

        // Parameter names
        if (request.getParameters() != null) {
            for (Map<String, Object> param : request.getParameters()) {
                String name = trimOrNull(toStr(param.get("name")));
                if (name != null && !allNames.add(name)) {
                    errors.add(String.format("Duplicate define name '%s' in parameters", name));
                }
            }
        }

        // Check conflicts with reserved eCQM population define names
        Set<String> reservedNames = new HashSet<>(EcqmConstants.POPULATION_KEY_TO_DEFINE.values());
        reservedNames.add(EcqmConstants.MEASURE_OBSERVATION);
        reservedNames.add(EcqmConstants.INITIAL_POPULATION_1);
        reservedNames.add(EcqmConstants.INITIAL_POPULATION_2);

        for (String name : allNames) {
            if (reservedNames.contains(name)) {
                errors.add(String.format("Name '%s' conflicts with reserved eCQM population define", name));
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private static String toStr(Object value) {
        return value instanceof String s ? s : null;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
