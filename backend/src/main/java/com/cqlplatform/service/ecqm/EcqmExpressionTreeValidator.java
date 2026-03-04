package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.ecqm.EcqmArtifactRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates eCQM artifact expression tree data for safety and consistency.
 */
@Component
@Slf4j
public class EcqmExpressionTreeValidator {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<svg[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<math[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<base[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
    };

    public void validate(EcqmArtifactRequest request) {
        if (request.getName() != null) {
            checkXss("name", request.getName());
        }
        if (request.getDescription() != null) {
            checkXss("description", request.getDescription());
        }

        validateTrees("populationGroups", request.getPopulationGroups());
        validateTrees("baseElements", request.getBaseElements());
        validateTrees("parameters", request.getParameters());
        validateTrees("supplementalData", request.getSupplementalData());
        validateTrees("stratifiers", request.getStratifiers());
    }

    @SuppressWarnings("unchecked")
    private void validateTrees(String fieldName, List<Map<String, Object>> trees) {
        if (trees == null) return;
        for (int i = 0; i < trees.size(); i++) {
            validateNode(fieldName + "[" + i + "]", trees.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(String path, Map<String, Object> node) {
        if (node == null) return;

        // Check string values for XSS
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if (entry.getValue() instanceof String) {
                checkXss(path + "." + entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Map) {
                validateNode(path + "." + entry.getKey(), (Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        validateNode(path + "." + entry.getKey() + "[" + i + "]", (Map<String, Object>) item);
                    } else if (item instanceof String) {
                        checkXss(path + "." + entry.getKey() + "[" + i + "]", (String) item);
                    }
                }
            }
        }
    }

    private void checkXss(String fieldPath, String value) {
        if (value == null) return;
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                throw new IllegalArgumentException(
                        String.format("Potentially unsafe content detected in field '%s'", fieldPath));
            }
        }
    }
}
