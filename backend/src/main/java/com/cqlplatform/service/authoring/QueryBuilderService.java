package com.cqlplatform.service.authoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service providing FHIR R4 resource property metadata and query operators
 * for the advanced query builder UI. Enables building element queries like
 * "Observation where status = 'final' and effective during LookBack".
 */
@Service
@Slf4j
public class QueryBuilderService {

    private static final List<Map<String, Object>> FHIR_RESOURCES = new ArrayList<>();
    private static final List<Map<String, Object>> OPERATORS = new ArrayList<>();

    static {
        // Common FHIR R4 resources with queryable properties
        addResource("Observation", List.of(
                prop("status", "code", List.of("registered", "preliminary", "final", "amended", "corrected", "cancelled", "entered-in-error", "unknown")),
                prop("category", "CodeableConcept", null),
                prop("code", "CodeableConcept", null),
                prop("effective", "dateTime", null),
                prop("value", "Quantity", null),
                prop("interpretation", "CodeableConcept", null),
                prop("bodySite", "CodeableConcept", null)
        ));

        addResource("Condition", List.of(
                prop("clinicalStatus", "code", List.of("active", "recurrence", "relapse", "inactive", "remission", "resolved")),
                prop("verificationStatus", "code", List.of("unconfirmed", "provisional", "differential", "confirmed", "refuted", "entered-in-error")),
                prop("category", "CodeableConcept", null),
                prop("severity", "CodeableConcept", null),
                prop("code", "CodeableConcept", null),
                prop("onset", "dateTime", null),
                prop("abatement", "dateTime", null),
                prop("recordedDate", "dateTime", null)
        ));

        addResource("MedicationRequest", List.of(
                prop("status", "code", List.of("active", "on-hold", "cancelled", "completed", "entered-in-error", "stopped", "draft", "unknown")),
                prop("intent", "code", List.of("proposal", "plan", "order", "original-order", "reflex-order", "filler-order", "instance-order", "option")),
                prop("category", "CodeableConcept", null),
                prop("medication", "CodeableConcept", null),
                prop("authoredOn", "dateTime", null),
                prop("dosageInstruction", "Dosage", null)
        ));

        addResource("Procedure", List.of(
                prop("status", "code", List.of("preparation", "in-progress", "not-done", "on-hold", "stopped", "completed", "entered-in-error", "unknown")),
                prop("code", "CodeableConcept", null),
                prop("performed", "dateTime", null),
                prop("category", "CodeableConcept", null),
                prop("bodySite", "CodeableConcept", null),
                prop("outcome", "CodeableConcept", null)
        ));

        addResource("Encounter", List.of(
                prop("status", "code", List.of("planned", "arrived", "triaged", "in-progress", "onleave", "finished", "cancelled", "entered-in-error", "unknown")),
                prop("class", "Coding", null),
                prop("type", "CodeableConcept", null),
                prop("period", "Period", null),
                prop("reasonCode", "CodeableConcept", null)
        ));

        addResource("AllergyIntolerance", List.of(
                prop("clinicalStatus", "code", List.of("active", "inactive", "resolved")),
                prop("verificationStatus", "code", List.of("unconfirmed", "confirmed", "refuted", "entered-in-error")),
                prop("type", "code", List.of("allergy", "intolerance")),
                prop("category", "code", List.of("food", "medication", "environment", "biologic")),
                prop("criticality", "code", List.of("low", "high", "unable-to-assess")),
                prop("code", "CodeableConcept", null)
        ));

        addResource("Immunization", List.of(
                prop("status", "code", List.of("completed", "entered-in-error", "not-done")),
                prop("vaccineCode", "CodeableConcept", null),
                prop("occurrence", "dateTime", null),
                prop("site", "CodeableConcept", null),
                prop("route", "CodeableConcept", null)
        ));

        addResource("ServiceRequest", List.of(
                prop("status", "code", List.of("draft", "active", "on-hold", "revoked", "completed", "entered-in-error", "unknown")),
                prop("intent", "code", List.of("proposal", "plan", "directive", "order", "original-order", "reflex-order", "filler-order", "instance-order", "option")),
                prop("code", "CodeableConcept", null),
                prop("authoredOn", "dateTime", null),
                prop("category", "CodeableConcept", null)
        ));

        addResource("DiagnosticReport", List.of(
                prop("status", "code", List.of("registered", "partial", "preliminary", "final", "amended", "corrected", "appended", "cancelled", "entered-in-error", "unknown")),
                prop("category", "CodeableConcept", null),
                prop("code", "CodeableConcept", null),
                prop("effective", "dateTime", null)
        ));

        // Operators
        addOperator("equals", "=", "Equality", List.of("code", "string", "integer", "decimal", "boolean"));
        addOperator("not_equals", "!=", "Not Equal", List.of("code", "string", "integer", "decimal", "boolean"));
        addOperator("greater_than", ">", "Greater Than", List.of("integer", "decimal", "Quantity", "dateTime"));
        addOperator("less_than", "<", "Less Than", List.of("integer", "decimal", "Quantity", "dateTime"));
        addOperator("greater_or_equal", ">=", "Greater or Equal", List.of("integer", "decimal", "Quantity", "dateTime"));
        addOperator("less_or_equal", "<=", "Less or Equal", List.of("integer", "decimal", "Quantity", "dateTime"));
        addOperator("in", "in", "In ValueSet", List.of("CodeableConcept", "Coding", "code"));
        addOperator("contains", "contains", "Contains", List.of("string"));
        addOperator("starts_with", "starts with", "Starts With", List.of("string"));
        addOperator("during", "during", "During Period", List.of("dateTime", "Period"));
        addOperator("before", "before", "Before", List.of("dateTime"));
        addOperator("after", "after", "After", List.of("dateTime"));
        addOperator("is_null", "is null", "Is Null", List.of("code", "string", "integer", "decimal", "boolean", "CodeableConcept", "Quantity", "dateTime"));
        addOperator("is_not_null", "is not null", "Is Not Null", List.of("code", "string", "integer", "decimal", "boolean", "CodeableConcept", "Quantity", "dateTime"));
    }

    public List<Map<String, Object>> getResources() {
        return Collections.unmodifiableList(FHIR_RESOURCES);
    }

    public List<Map<String, Object>> getOperators() {
        return Collections.unmodifiableList(OPERATORS);
    }

    public Optional<Map<String, Object>> getResource(String name) {
        return FHIR_RESOURCES.stream()
                .filter(r -> name.equals(r.get("name")))
                .findFirst();
    }

    public List<Map<String, Object>> getOperatorsForType(String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : OPERATORS) {
            @SuppressWarnings("unchecked")
            List<String> types = (List<String>) op.get("applicableTypes");
            if (types != null && types.contains(type)) {
                result.add(op);
            }
        }
        return result;
    }

    private static void addResource(String name, List<Map<String, Object>> properties) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", name);
        resource.put("properties", properties);
        FHIR_RESOURCES.add(resource);
    }

    private static Map<String, Object> prop(String name, String type, List<String> values) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", name);
        property.put("type", type);
        if (values != null) {
            property.put("values", values);
        }
        return property;
    }

    private static void addOperator(String id, String symbol, String label, List<String> applicableTypes) {
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("id", id);
        operator.put("symbol", symbol);
        operator.put("label", label);
        operator.put("applicableTypes", applicableTypes);
        OPERATORS.add(operator);
    }
}
