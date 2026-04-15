package com.cqlplatform.validation;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsServiceDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines required context fields per CDS hook type, per the CDS Hooks specification.
 * Single source of truth for context requirements — used by invocation validation
 * and discovery response enrichment.
 */
public final class HookContextRequirements {

    /**
     * Required context fields for each hook type.
     * Field names match CdsRequest.CdsContext property names.
     */
    private static final Map<String, List<String>> REQUIRED_CONTEXT = Map.of(
            "patient-view", List.of("userId", "patientId"),
            "order-select", List.of("userId", "patientId", "selections", "draftOrders"),
            "order-sign", List.of("userId", "patientId", "draftOrders"),
            "encounter-start", List.of("userId", "patientId", "encounterId"),
            "encounter-discharge", List.of("userId", "patientId", "encounterId"),
            "appointment-book", List.of("userId", "patientId", "appointments")
    );

    /**
     * Context field type information: "string" for simple IDs, "object" for FHIR Bundles.
     */
    private static final Map<String, String> FIELD_TYPES = Map.of(
            "userId", "string",
            "patientId", "string",
            "encounterId", "string",
            "draftOrders", "object",
            "selections", "object",
            "appointments", "object"
    );

    private HookContextRequirements() {
    }

    /**
     * Returns the set of all supported hook types.
     */
    public static Set<String> getSupportedHooks() {
        return REQUIRED_CONTEXT.keySet();
    }

    /**
     * Returns the list of required context field names for the given hook type.
     */
    public static List<String> getRequiredFields(String hookType) {
        return REQUIRED_CONTEXT.getOrDefault(hookType, List.of());
    }

    /**
     * Returns the type of a context field ("string" or "object").
     */
    public static String getFieldType(String fieldName) {
        return FIELD_TYPES.getOrDefault(fieldName, "string");
    }

    /**
     * Returns context field definitions for a hook type, suitable for discovery responses.
     */
    public static List<CdsServiceDefinition.ContextField> getContextFieldDefinitions(String hookType) {
        List<String> required = getRequiredFields(hookType);
        return required.stream()
                .map(name -> CdsServiceDefinition.ContextField.builder()
                        .name(name)
                        .required(true)
                        .type(getFieldType(name))
                        .build())
                .toList();
    }

    /**
     * Validates that the given context contains all required fields for the hook type.
     *
     * @throws ValidationException if any required fields are missing, with details listing each one
     */
    public static void validateContext(String hookType, CdsRequest.CdsContext context) {
        List<String> requiredFields = getRequiredFields(hookType);
        if (requiredFields.isEmpty()) {
            return;
        }

        if (context == null) {
            throw new ValidationException(
                    "Context is required for hook type '" + hookType + "'",
                    requiredFields.stream()
                            .map(f -> "Missing required context field: " + f)
                            .toList()
            );
        }

        List<String> missing = new ArrayList<>();
        for (String field : requiredFields) {
            if (!hasField(context, field)) {
                missing.add(field);
            }
        }

        if (!missing.isEmpty()) {
            throw new ValidationException(
                    "Missing required context fields for hook '" + hookType + "': " + missing,
                    missing.stream()
                            .map(f -> "Missing required context field: " + f)
                            .toList()
            );
        }
    }

    private static boolean hasField(CdsRequest.CdsContext context, String field) {
        return switch (field) {
            case "userId" -> isNotBlank(context.getUserId());
            case "patientId" -> isNotBlank(context.getPatientId());
            case "encounterId" -> isNotBlank(context.getEncounterId());
            case "draftOrders" -> context.getDraftOrders() != null;
            case "selections" -> context.getSelections() != null;
            case "appointments" -> context.getAppointments() != null;
            default -> false;
        };
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
