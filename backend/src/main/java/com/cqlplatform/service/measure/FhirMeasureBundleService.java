package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Assembles a complete FHIR Bundle containing all resources needed for a standalone eCQM:
 * Measure + Library (main + included) + ValueSets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirMeasureBundleService {

    private final FhirMeasureService fhirMeasureService;
    private final FhirLibraryService fhirLibraryService;
    private final CqlLibraryService cqlLibraryService;
    private final CqlTranslationService translationService;
    private final MeasureDefinitionService definitionService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ObjectNode exportAsBundle(Long measureId) {
        MeasureDefinition definition = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));

        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "bundle-" + measureId);
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());

        ObjectNode identifier = bundle.putObject("identifier");
        identifier.put("system", "urn:ietf:rfc:3986");
        identifier.put("value", "urn:uuid:" + UUID.randomUUID());

        ArrayNode entries = bundle.putArray("entry");

        // 1. Add Measure resource
        ObjectNode measureJson = fhirMeasureService.exportAsFhirMeasure(measureId);
        enhanceMeasureResource(measureJson, definition);
        addEntry(entries, measureJson);

        // 2. Build and add Library resources from CQL
        if (definition.getCqlContent() != null && !definition.getCqlContent().isBlank()) {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(definition.getCqlContent());
            CqlTranslationResponse response = translationService.translate(request);

            // Main Library resource
            ObjectNode mainLibrary = buildLibraryResource(
                    definition.getCqlContent(),
                    response.getElmJson(),
                    response.getMetadata() != null ? response.getMetadata().getLibraryId() : definition.getName(),
                    response.getMetadata() != null ? response.getMetadata().getLibraryVersion() : definition.getVersion()
            );
            addEntry(entries, mainLibrary);

            // Add canonical library reference to Measure
            String canonicalUrl = "Library/" + (response.getMetadata() != null ? response.getMetadata().getLibraryId() : definition.getName());
            ArrayNode libraryArray = measureJson.putArray("library");
            libraryArray.add(canonicalUrl);

            // 3. Recursively resolve included libraries
            if (response.getMetadata() != null && response.getMetadata().getIncludes() != null) {
                Set<String> visited = new HashSet<>();
                for (String include : response.getMetadata().getIncludes()) {
                    collectIncludedLibraries(include, entries, visited);
                }
            }

            // 4. Add referenced ValueSets as stub resources
            if (response.getMetadata() != null && response.getMetadata().getValueSets() != null) {
                for (String vsUrl : response.getMetadata().getValueSets()) {
                    ObjectNode vsResource = buildValueSetStub(vsUrl);
                    addEntry(entries, vsResource);
                }
            }
        }

        return bundle;
    }

    public String exportAsBundleXml(Long measureId) {
        ObjectNode bundle = exportAsBundle(measureId);
        return convertJsonBundleToSimpleXml(bundle);
    }

    public String exportCqlOnly(Long measureId) {
        MeasureDefinition definition = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        return definition.getCqlContent() != null ? definition.getCqlContent() : "";
    }

    public String exportElmOnly(Long measureId) {
        MeasureDefinition definition = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        if (definition.getCqlContent() == null || definition.getCqlContent().isBlank()) {
            return "{}";
        }
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(definition.getCqlContent());
        CqlTranslationResponse response = translationService.translate(request);
        return response.getElmJson() != null ? response.getElmJson() : "{}";
    }

    private void enhanceMeasureResource(ObjectNode measure, MeasureDefinition definition) {
        // Add effectivePeriod if not present
        if (!measure.has("effectivePeriod")) {
            ObjectNode effectivePeriod = measure.putObject("effectivePeriod");
            effectivePeriod.put("start", definition.getCreatedAt() != null
                    ? definition.getCreatedAt().toString().substring(0, 10)
                    : java.time.LocalDate.now().withDayOfYear(1).toString());
        }

        // Add relatedArtifact references
        if (definition.getReferences() != null && !definition.getReferences().isEmpty()) {
            ArrayNode relatedArtifact = measure.putArray("relatedArtifact");
            for (var ref : definition.getReferences()) {
                ObjectNode artifact = relatedArtifact.addObject();
                artifact.put("type", ref.getType() != null ? ref.getType() : "documentation");
                artifact.put("display", ref.getReference());
            }
        }
    }

    private ObjectNode buildLibraryResource(String cql, String elmJson, String name, String version) {
        ObjectNode library = MAPPER.createObjectNode();
        library.put("resourceType", "Library");
        library.put("id", name);
        library.put("name", name);
        library.put("version", version != null ? version : "1.0.0");
        library.put("status", "active");

        ObjectNode type = library.putObject("type");
        ArrayNode typeCoding = type.putArray("coding");
        ObjectNode typeCode = typeCoding.addObject();
        typeCode.put("system", "http://terminology.hl7.org/CodeSystem/library-type");
        typeCode.put("code", "logic-library");

        ArrayNode content = library.putArray("content");

        // CQL attachment
        ObjectNode cqlAttachment = content.addObject();
        cqlAttachment.put("contentType", "text/cql");
        cqlAttachment.put("data", Base64.getEncoder().encodeToString(cql.getBytes(StandardCharsets.UTF_8)));

        // ELM attachment
        if (elmJson != null) {
            ObjectNode elmAttachment = content.addObject();
            elmAttachment.put("contentType", "application/elm+json");
            elmAttachment.put("data", Base64.getEncoder().encodeToString(elmJson.getBytes(StandardCharsets.UTF_8)));
        }

        return library;
    }

    private void collectIncludedLibraries(String include, ArrayNode entries, Set<String> visited) {
        // include format: "LibraryName version 'x.y.z'" or just "LibraryName"
        String depName = include.split("\\s+")[0].replace("\"", "");
        if (visited.contains(depName)) return;
        visited.add(depName);

        cqlLibraryService.getLatestLibrary(depName).ifPresent(lib -> {
            ObjectNode libResource = buildLibraryResource(
                    lib.getCqlContent(), lib.getElmJson(), lib.getName(), lib.getVersion());
            addEntry(entries, libResource);

            // Recurse
            if (lib.getDependencies() != null) {
                for (String dep : lib.getDependencies()) {
                    collectIncludedLibraries(dep, entries, visited);
                }
            }
        });
    }

    private ObjectNode buildValueSetStub(String vsUrl) {
        ObjectNode vs = MAPPER.createObjectNode();
        vs.put("resourceType", "ValueSet");
        // Extract OID or identifier from URL
        String id = vsUrl;
        if (vsUrl.contains("/")) {
            id = vsUrl.substring(vsUrl.lastIndexOf('/') + 1);
        }
        vs.put("id", id);
        vs.put("url", vsUrl);
        vs.put("status", "active");
        return vs;
    }

    private void addEntry(ArrayNode entries, ObjectNode resource) {
        ObjectNode entry = entries.addObject();
        String resourceType = resource.path("resourceType").asText("Resource");
        String id = resource.path("id").asText("");
        entry.put("fullUrl", "urn:uuid:" + UUID.randomUUID());
        entry.set("resource", resource);
    }

    private String convertJsonBundleToSimpleXml(ObjectNode bundle) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Bundle xmlns=\"http://hl7.org/fhir\">\n");
        xml.append("  <id value=\"").append(escapeXml(bundle.path("id").asText())).append("\"/>\n");
        xml.append("  <type value=\"collection\"/>\n");
        xml.append("  <timestamp value=\"").append(escapeXml(bundle.path("timestamp").asText())).append("\"/>\n");

        var entries = bundle.path("entry");
        if (entries.isArray()) {
            for (var entry : entries) {
                xml.append("  <entry>\n");
                xml.append("    <fullUrl value=\"").append(escapeXml(entry.path("fullUrl").asText())).append("\"/>\n");
                var resource = entry.path("resource");
                String resourceType = resource.path("resourceType").asText("Resource");
                xml.append("    <resource>\n");
                xml.append("      <").append(resourceType).append(">\n");
                xml.append("        <id value=\"").append(escapeXml(resource.path("id").asText())).append("\"/>\n");
                if (resource.has("name")) {
                    xml.append("        <name value=\"").append(escapeXml(resource.path("name").asText())).append("\"/>\n");
                }
                if (resource.has("version")) {
                    xml.append("        <version value=\"").append(escapeXml(resource.path("version").asText())).append("\"/>\n");
                }
                if (resource.has("status")) {
                    xml.append("        <status value=\"").append(escapeXml(resource.path("status").asText())).append("\"/>\n");
                }
                if (resource.has("url")) {
                    xml.append("        <url value=\"").append(escapeXml(resource.path("url").asText())).append("\"/>\n");
                }
                xml.append("      </").append(resourceType).append(">\n");
                xml.append("    </resource>\n");
                xml.append("  </entry>\n");
            }
        }

        xml.append("</Bundle>\n");
        return xml.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
