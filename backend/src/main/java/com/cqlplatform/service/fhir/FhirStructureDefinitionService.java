package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimeChildResourceBlockDefinition;
import ca.uhn.fhir.context.RuntimeChildResourceDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import com.cqlplatform.model.fhir.ElementMetadata;
import com.cqlplatform.model.fhir.ResourceElementMetadata;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FhirStructureDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(FhirStructureDefinitionService.class);

    private static final int MAX_DEPTH = 3;

    // Infrastructure / conformance resource types excluded from the visual builder
    private static final Set<String> EXCLUDED_RESOURCE_TYPES = Set.of(
            // Conformance & Terminology
            "StructureDefinition", "StructureMap", "ValueSet", "CodeSystem", "ConceptMap",
            "NamingSystem", "TerminologyCapabilities",
            // Capability & Search
            "CapabilityStatement", "SearchParameter", "CompartmentDefinition", "OperationDefinition",
            // Implementation Support
            "ImplementationGuide", "GraphDefinition", "ExampleScenario",
            // Exchange infrastructure
            "Bundle", "OperationOutcome", "Parameters", "Binary",
            "MessageHeader", "MessageDefinition",
            // Testing
            "TestScript", "TestReport",
            // Other infrastructure
            "Subscription", "EventDefinition"
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
    private final CachingValidationSupport validationSupport;
    private final ConcurrentHashMap<String, StructureDefinition> structureDefinitionCache = new ConcurrentHashMap<>();

    public FhirStructureDefinitionService(FhirContext fhirContext) {
        this.fhirContext = fhirContext;

        DefaultProfileValidationSupport defaultSupport = new DefaultProfileValidationSupport(fhirContext);
        InMemoryTerminologyServerValidationSupport terminologySupport = new InMemoryTerminologyServerValidationSupport(fhirContext);
        CommonCodeSystemsTerminologyService commonCodeSystems = new CommonCodeSystemsTerminologyService(fhirContext);

        ValidationSupportChain chain = new ValidationSupportChain(
                defaultSupport, terminologySupport, commonCodeSystems
        );
        this.validationSupport = new CachingValidationSupport(chain);
    }

    public List<String> getSupportedResourceTypes() {
        return fhirContext.getResourceTypes().stream()
                .filter(type -> !EXCLUDED_RESOURCE_TYPES.contains(type))
                .sorted()
                .collect(Collectors.toList());
    }

    public ResourceElementMetadata getResourceMetadata(String resourceType) {
        return cache.computeIfAbsent(resourceType, this::buildResourceMetadata);
    }

    private ResourceElementMetadata buildResourceMetadata(String resourceType) {
        RuntimeResourceDefinition resourceDef = fhirContext.getResourceDefinition(resourceType);
        List<ElementMetadata> elements = buildElements(resourceDef, resourceType, 0, null);
        return new ResourceElementMetadata(resourceType, elements);
    }

    private List<ElementMetadata> buildElements(
            BaseRuntimeElementCompositeDefinition<?> compositeDef,
            String parentPath,
            int depth,
            String datatypeContext
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
                ElementMetadata element = buildElement(childDef, parentPath, depth, datatypeContext);
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
            int depth,
            String datatypeContext
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
        List<String> boundCodes = Collections.emptyList();
        var bindingInfo = extractBinding(path, datatypeContext);
        if (bindingInfo != null) {
            bindingStrength = bindingInfo.strength;
            bindingValueSetUrl = bindingInfo.valueSetUrl;
            if (("required".equals(bindingStrength) || "extensible".equals(bindingStrength))
                    && bindingValueSetUrl != null) {
                boundCodes = expandValueSetCodes(bindingValueSetUrl);
            }
        }

        List<ElementMetadata> children = Collections.emptyList();
        if (elementDef instanceof BaseRuntimeElementCompositeDefinition<?> compositeChild) {
            if (!PRIMITIVE_TYPES.contains(typeName)) {
                children = buildElements(compositeChild, path, depth + 1, typeName);
            }
        }

        return new ElementMetadata(
                elementName, path, typeName,
                isArray, isRequired, min, maxStr,
                false, Collections.emptyList(),
                bindingStrength, bindingValueSetUrl, boundCodes,
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
                null, null, Collections.emptyList(),
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
                null, null, Collections.emptyList(),
                Collections.emptyList(), null,
                referenceTargets
        );
    }

    // --- Binding extraction from StructureDefinition snapshots ---

    private StructureDefinition getStructureDefinition(String url) {
        return structureDefinitionCache.computeIfAbsent(url, u -> {
            IBaseResource resource = validationSupport.fetchStructureDefinition(u);
            if (resource instanceof StructureDefinition sd) {
                return sd;
            }
            return null;
        });
    }

    /**
     * Binding info holder
     */
    private record BindingInfo(String strength, String valueSetUrl) {}

    /**
     * Find binding for a given element path by looking up the StructureDefinition snapshot.
     * Tries resource-level path first (e.g. Patient.gender), then datatype path (e.g. HumanName.use).
     */
    private BindingInfo extractBinding(String elementPath, String datatypeContext) {
        // Try resource-level path first (e.g. "Patient.gender")
        BindingInfo info = findBindingForPath(elementPath);
        if (info != null) {
            return info;
        }

        // Try datatype path (e.g. "HumanName.use" for "Patient.name.use")
        if (datatypeContext != null) {
            String elementName = elementPath.substring(elementPath.lastIndexOf('.') + 1);
            String datatypePath = datatypeContext + "." + elementName;
            info = findBindingForPath(datatypePath);
            if (info != null) {
                return info;
            }
        }

        return null;
    }

    /**
     * Look up a specific element path in the corresponding StructureDefinition snapshot.
     */
    private BindingInfo findBindingForPath(String elementPath) {
        // Derive the resource/type name from the path (first segment)
        int dotIdx = elementPath.indexOf('.');
        if (dotIdx < 0) {
            return null;
        }
        String typeName = elementPath.substring(0, dotIdx);

        // Build the StructureDefinition URL
        String sdUrl = com.cqlplatform.model.fhir.FhirCodeSystemConstants.STRUCTURE_DEFINITION_BASE + typeName;
        StructureDefinition sd = getStructureDefinition(sdUrl);
        if (sd == null || sd.getSnapshot() == null) {
            return null;
        }

        for (StructureDefinition.StructureDefinitionSnapshotComponent snap : List.of(sd.getSnapshot())) {
            for (ElementDefinition elem : snap.getElement()) {
                if (elementPath.equals(elem.getPath()) && elem.hasBinding()) {
                    ElementDefinition.ElementDefinitionBindingComponent binding = elem.getBinding();
                    String strength = binding.getStrength() != null
                            ? binding.getStrength().toCode()
                            : null;
                    String vsUrl = binding.getValueSet();
                    if (strength != null) {
                        return new BindingInfo(strength, vsUrl);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Expand a ValueSet URL into its list of codes using the validation support chain.
     */
    private List<String> expandValueSetCodes(String valueSetUrl) {
        try {
            IBaseResource vsResource = validationSupport.fetchValueSet(valueSetUrl);
            if (!(vsResource instanceof ValueSet vs)) {
                return Collections.emptyList();
            }

            // Try expansion via validation support
            ValidationSupportContext vsContext = new ValidationSupportContext(validationSupport);
            var expansion = validationSupport.expandValueSet(vsContext, null, vsResource);
            if (expansion != null && expansion.getValueSet() instanceof ValueSet expandedVs) {
                ValueSet.ValueSetExpansionComponent exp = expandedVs.getExpansion();
                if (exp != null && !exp.getContains().isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (ValueSet.ValueSetExpansionContainsComponent c : exp.getContains()) {
                        if (c.getCode() != null) {
                            codes.add(c.getCode());
                        }
                    }
                    return codes;
                }
            }

            // Fallback: extract codes directly from compose.include if expansion fails
            if (vs.hasCompose()) {
                List<String> codes = new ArrayList<>();
                for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
                    for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
                        if (concept.getCode() != null) {
                            codes.add(concept.getCode());
                        }
                    }
                }
                if (!codes.isEmpty()) {
                    return codes;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to expand ValueSet {}: {}", valueSetUrl, e.getMessage());
        }
        return Collections.emptyList();
    }

    // --- Type name helpers ---

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
}
