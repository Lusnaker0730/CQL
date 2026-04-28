package com.cqlplatform.service.authoring;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.authoring.ArtifactRequest;
import com.cqlplatform.model.authoring.AuthoringConstants;
import com.cqlplatform.validation.ModifierValueValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionTreeValidator {

    private final TemplateService templateService;
    private final ModifierService modifierService;

    /**
     * Allowed characters for CQL identifiers (define names, parameter names, etc.).
     * Permits alphanumeric, CJK characters, spaces, hyphens, underscores, dots, and common punctuation.
     * Disallows quotes, backslashes, and control characters that could be used for CQL injection.
     */
    private static final Pattern CQL_IDENTIFIER_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}_\\-. ]{1,255}$");

    private static final Set<String> RESERVED_DEFINE_NAMES = Set.of(
            AuthoringConstants.DEF_MEETS_INCLUSION,
            AuthoringConstants.DEF_MEETS_EXCLUSION,
            AuthoringConstants.DEF_IN_POPULATION,
            AuthoringConstants.DEF_RECOMMENDATION,
            AuthoringConstants.DEF_ERRORS);

    public void validate(ArtifactRequest request) {
        List<String> errors = new ArrayList<>();

        validateTree(request.getExpTreeInclude(), "expTreeInclude", errors);
        validateTree(request.getExpTreeExclude(), "expTreeExclude", errors);
        validateNodeList(request.getBaseElements(), "baseElements", errors);
        validateNodeList(request.getSubpopulations(), "subpopulations", errors);

        validateDefineNames(request, errors);

        if (!errors.isEmpty()) {
            log.warn("Expression tree validation errors: {}", errors);
            throw new ValidationException("Invalid expression tree elements", errors);
        }
    }

    // ------------------------------------------------------------------
    // Define-name uniqueness: same-category duplicates, cross-category
    // collisions, and reserved system name conflicts.
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void validateDefineNames(ArtifactRequest request, List<String> errors) {
        // Collect names per category
        List<String> beNames = new ArrayList<>();
        if (request.getBaseElements() != null) {
            for (Map<String, Object> be : request.getBaseElements()) {
                String name = trimOrNull((String) be.get("name"));
                if (name != null) {
                    beNames.add(name);
                }
            }
        }

        List<String> spNames = new ArrayList<>();
        if (request.getSubpopulations() != null) {
            for (Map<String, Object> sp : request.getSubpopulations()) {
                if (Boolean.TRUE.equals(sp.get("special"))) {
                    continue;
                }
                String name = trimOrNull((String) sp.get("subpopulationName"));
                if (name != null) {
                    spNames.add(name);
                }
            }
        }

        List<String> paramNames = new ArrayList<>();
        if (request.getParameters() != null) {
            for (Map<String, Object> param : request.getParameters()) {
                String name = trimOrNull((String) param.get("name"));
                if (name != null) {
                    paramNames.add(name);
                }
            }
        }

        // 0. Identifier character validation
        for (String name : beNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("baseElements: name '%s' contains invalid characters", name));
            }
        }
        for (String name : spNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("subpopulations: name '%s' contains invalid characters", name));
            }
        }
        for (String name : paramNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("parameters: name '%s' contains invalid characters", name));
            }
        }

        // 1. Same-category duplicates
        findDuplicates(beNames, "baseElements").forEach(dup ->
                errors.add(String.format("baseElements: duplicate name '%s'", dup)));
        findDuplicates(spNames, "subpopulations").forEach(dup ->
                errors.add(String.format("subpopulations: duplicate name '%s'", dup)));
        findDuplicates(paramNames, "parameters").forEach(dup ->
                errors.add(String.format("parameters: duplicate name '%s'", dup)));

        // 2. Cross-category collisions (all share CQL define namespace)
        Set<String> defineNames = new LinkedHashSet<>();
        for (String name : beNames) {
            defineNames.add(name);
        }
        for (String name : spNames) {
            if (!defineNames.add(name)) {
                errors.add(String.format(
                        "subpopulations: name '%s' conflicts with a base element", name));
            }
        }

        // 3. Reserved system define names
        for (String name : beNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "baseElements: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
        for (String name : spNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "subpopulations: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
        for (String name : paramNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "parameters: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
    }

    private static boolean isReservedDefineName(String name) {
        if (RESERVED_DEFINE_NAMES.contains(name)) {
            return true;
        }
        // Recommendations can be numbered: "Recommendation 1", "Recommendation 2", ...
        return name.startsWith(AuthoringConstants.DEF_RECOMMENDATION + " ");
    }

    private static Set<String> findDuplicates(List<String> names, String category) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------
    // Element type / modifier ID validation
    // ------------------------------------------------------------------

    private void validateTree(Map<String, Object> tree, String treeName, List<String> errors) {
        if (tree == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childInstances =
                (List<Map<String, Object>>) tree.get("childInstances");
        if (childInstances != null) {
            for (Map<String, Object> child : childInstances) {
                validateNode(child, treeName, errors);
            }
        }
    }

    private void validateNodeList(List<Map<String, Object>> nodes, String listName, List<String> errors) {
        if (nodes == null) {
            return;
        }
        for (Map<String, Object> node : nodes) {
            validateNode(node, listName, errors);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(Map<String, Object> node, String path, List<String> errors) {
        if (node == null) {
            return;
        }

        // Conjunction nodes recurse into childInstances
        Boolean conjunction = toBoolean(node.get("conjunction"));
        if (Boolean.TRUE.equals(conjunction)) {
            List<Map<String, Object>> children =
                    (List<Map<String, Object>>) node.get("childInstances");
            if (children != null) {
                for (Map<String, Object> child : children) {
                    validateNode(child, path, errors);
                }
            }
        }

        // Validate element type
        String type = (String) node.get("type");
        if (type != null && !type.isBlank() && !templateService.isValidElementType(type)) {
            String name = (String) node.get("name");
            errors.add(String.format("%s: unknown element type '%s'%s",
                    path, type, name != null ? " (name: " + name + ")" : ""));
        }

        // Validate external CQL library names
        if ("externalCqlRef".equals(type)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    String fieldId = (String) field.get("id");
                    if ("library_name".equals(fieldId) || "library_version".equals(fieldId)) {
                        Object val = field.get("value");
                        if (val instanceof String && !((String) val).isEmpty()
                                && !CQL_IDENTIFIER_PATTERN.matcher((String) val).matches()) {
                            errors.add(String.format("%s: %s '%s' contains invalid characters",
                                    path, fieldId, val));
                        }
                    }
                }
            }
        }

        // Validate modifier IDs
        List<Map<String, Object>> modifiers =
                (List<Map<String, Object>>) node.get("modifiers");
        if (modifiers != null) {
            for (Map<String, Object> modifier : modifiers) {
                String modId = (String) modifier.get("id");
                if (modId != null && !modId.isBlank() && !modifierService.isValidModifierId(modId)) {
                    String modName = (String) modifier.get("name");
                    errors.add(String.format("%s: unknown modifier id '%s'%s",
                            path, modId, modName != null ? " (name: " + modName + ")" : ""));
                }
                ModifierValueValidator.validate(modifier, path, errors);
            }
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }
}
