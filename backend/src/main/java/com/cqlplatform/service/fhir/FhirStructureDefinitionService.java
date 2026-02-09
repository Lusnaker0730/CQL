package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimeChildResourceBlockDefinition;
import ca.uhn.fhir.context.RuntimeChildResourceDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import com.cqlplatform.model.fhir.ElementMetadata;
import com.cqlplatform.model.fhir.ResourceElementMetadata;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FhirStructureDefinitionService {

    private static final int MAX_DEPTH = 3;

    private static final List<String> SUPPORTED_RESOURCE_TYPES = List.of(
            "Patient", "Encounter", "Condition", "Observation", "Procedure",
            "MedicationRequest", "Coverage", "DiagnosticReport", "Immunization",
            "AllergyIntolerance", "ServiceRequest", "CarePlan", "Goal",
            "FamilyMemberHistory", "DeviceUseStatement"
    );

    private static final Set<String> SKIP_ELEMENTS = Set.of(
            "id", "meta", "implicitRules", "language", "text",
            "contained", "extension", "modifierExtension"
    );

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "string", "boolean", "integer", "decimal", "uri", "url", "canonical",
            "date", "dateTime", "instant", "time", "code", "oid", "id", "uuid",
            "markdown", "base64Binary", "positiveInt", "unsignedInt", "xhtml"
    );

    private final FhirContext fhirContext;
    private final ConcurrentHashMap<String, ResourceElementMetadata> cache = new ConcurrentHashMap<>();

    public FhirStructureDefinitionService(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public List<String> getSupportedResourceTypes() {
        return SUPPORTED_RESOURCE_TYPES;
    }

    public ResourceElementMetadata getResourceMetadata(String resourceType) {
        return cache.computeIfAbsent(resourceType, this::buildResourceMetadata);
    }

    private ResourceElementMetadata buildResourceMetadata(String resourceType) {
        RuntimeResourceDefinition resourceDef = fhirContext.getResourceDefinition(resourceType);
        List<ElementMetadata> elements = buildElements(resourceDef, resourceType, 0);
        return new ResourceElementMetadata(resourceType, elements);
    }

    private List<ElementMetadata> buildElements(
            BaseRuntimeElementCompositeDefinition<?> compositeDef,
            String parentPath,
            int depth
    ) {
        if (depth >= MAX_DEPTH) {
            return Collections.emptyList();
        }

        List<ElementMetadata> elements = new ArrayList<>();

        for (BaseRuntimeChildDefinition childDef : compositeDef.getChildren()) {
            String elementName = childDef.getElementName();

            if (SKIP_ELEMENTS.contains(elementName)) {
                continue;
            }

            try {
                ElementMetadata element = buildElement(childDef, parentPath, depth);
                if (element != null) {
                    elements.add(element);
                }
            } catch (Exception e) {
                // Skip elements that can't be introspected
            }
        }

        return elements;
    }

    private ElementMetadata buildElement(
            BaseRuntimeChildDefinition childDef,
            String parentPath,
            int depth
    ) {
        String elementName = childDef.getElementName();
        String path = parentPath + "." + elementName;
        int min = childDef.getMin();
        int max = childDef.getMax();
        boolean isArray = max != 1;
        boolean isRequired = min > 0;
        String maxStr = max == -1 ? "*" : String.valueOf(max);

        // Handle choice types (e.g. value[x])
        if (childDef instanceof RuntimeChildChoiceDefinition choiceDef) {
            return buildChoiceElement(choiceDef, elementName, path, min, maxStr, isArray, isRequired, depth);
        }

        // Handle references
        if (childDef instanceof RuntimeChildResourceDefinition resourceChildDef) {
            return buildReferenceElement(resourceChildDef, elementName, path, min, maxStr, isArray, isRequired);
        }

        // Handle backbone/composite and primitive types
        BaseRuntimeElementDefinition<?> elementDef = childDef.getChildByName(elementName);
        if (elementDef == null) {
            return null;
        }

        String typeName = getTypeName(elementDef);
        String description = getDescription(elementDef);

        // Binding info
        String bindingStrength = null;
        String bindingValueSetUrl = null;
        var bindingInfo = extractBinding(childDef, elementName);
        if (bindingInfo != null) {
            bindingStrength = bindingInfo[0];
            bindingValueSetUrl = bindingInfo[1];
        }

        List<ElementMetadata> children = Collections.emptyList();
        if (elementDef instanceof BaseRuntimeElementCompositeDefinition<?> compositeChild) {
            if (!PRIMITIVE_TYPES.contains(typeName)) {
                children = buildElements(compositeChild, path, depth + 1);
            }
        }

        return new ElementMetadata(
                elementName, path, typeName,
                isArray, isRequired, min, maxStr,
                false, Collections.emptyList(),
                bindingStrength, bindingValueSetUrl,
                children, description,
                Collections.emptyList()
        );
    }

    private ElementMetadata buildChoiceElement(
            RuntimeChildChoiceDefinition choiceDef,
            String elementName,
            String path,
            int min, String max,
            boolean isArray, boolean isRequired,
            int depth
    ) {
        List<String> choiceTypes = new ArrayList<>();
        for (Class<? extends org.hl7.fhir.instance.model.api.IBase> type : choiceDef.getChoices()) {
            String name = type.getSimpleName();
            // Convert HAPI class names to FHIR type names
            name = convertHapiTypeName(name);
            choiceTypes.add(name);
        }

        return new ElementMetadata(
                elementName, path, "choice",
                isArray, isRequired, min, max,
                true, choiceTypes,
                null, null,
                Collections.emptyList(), null,
                Collections.emptyList()
        );
    }

    private ElementMetadata buildReferenceElement(
            RuntimeChildResourceDefinition resourceChildDef,
            String elementName,
            String path,
            int min, String max,
            boolean isArray, boolean isRequired
    ) {
        List<String> referenceTargets = new ArrayList<>();
        for (Class<? extends IBaseResource> resClass : resourceChildDef.getResourceTypes()) {
            RuntimeResourceDefinition resDef = fhirContext.getResourceDefinition(resClass);
            referenceTargets.add(resDef.getName());
        }

        return new ElementMetadata(
                elementName, path, "Reference",
                isArray, isRequired, min, max,
                false, Collections.emptyList(),
                null, null,
                Collections.emptyList(), null,
                referenceTargets
        );
    }

    private String getTypeName(BaseRuntimeElementDefinition<?> elementDef) {
        String name = elementDef.getName();
        if (name == null || name.isEmpty()) {
            name = elementDef.getClass().getSimpleName()
                    .replace("RuntimePrimitiveDatatypeDefinition", "")
                    .replace("RuntimeCompositeDatatypeDefinition", "");
        }
        return convertHapiTypeName(name);
    }

    private String convertHapiTypeName(String name) {
        return switch (name) {
            case "StringType" -> "string";
            case "BooleanType" -> "boolean";
            case "IntegerType" -> "integer";
            case "DecimalType" -> "decimal";
            case "DateType" -> "date";
            case "DateTimeType" -> "dateTime";
            case "InstantType" -> "instant";
            case "TimeType" -> "time";
            case "UriType" -> "uri";
            case "UrlType" -> "url";
            case "CanonicalType" -> "canonical";
            case "CodeType" -> "code";
            case "OidType" -> "oid";
            case "IdType" -> "id";
            case "UuidType" -> "uuid";
            case "MarkdownType" -> "markdown";
            case "Base64BinaryType" -> "base64Binary";
            case "PositiveIntType" -> "positiveInt";
            case "UnsignedIntType" -> "unsignedInt";
            case "Enumeration" -> "code";
            default -> name;
        };
    }

    private String getDescription(BaseRuntimeElementDefinition<?> elementDef) {
        // HAPI doesn't directly expose the description; return null
        return null;
    }

    private String[] extractBinding(BaseRuntimeChildDefinition childDef, String elementName) {
        // Try to extract binding information from the StructureDefinition
        try {
            BaseRuntimeElementDefinition<?> childElementDef = childDef.getChildByName(elementName);
            if (childElementDef != null) {
                // Check for code type — these often have bindings
                String typeName = getTypeName(childElementDef);
                if ("code".equals(typeName) || "Coding".equals(typeName) || "CodeableConcept".equals(typeName)) {
                    // Try to get the binding from StructureDefinition snapshot
                    return extractBindingFromStructureDefinition(childDef);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String[] extractBindingFromStructureDefinition(BaseRuntimeChildDefinition childDef) {
        // HAPI's RuntimeChildDefinition doesn't directly expose bindings in a simple way
        // For commonly known bindings, we could hard-code them, but for now return null
        // The frontend will use freetext input for codes without known bindings
        return null;
    }
}
