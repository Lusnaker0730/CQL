package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirLibraryService {

    private final CqlLibraryService libraryService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ObjectNode exportAsFhirLibrary(String libraryId) {
        CqlLibrary library = libraryService.getLibrary(libraryId)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryId));

        ObjectNode fhirLibrary = MAPPER.createObjectNode();
        fhirLibrary.put("resourceType", "Library");
        fhirLibrary.put("id", library.getId());
        fhirLibrary.put("name", library.getName());
        fhirLibrary.put("version", library.getVersion());
        fhirLibrary.put("status", "active");

        // Type: logic-library
        ObjectNode type = fhirLibrary.putObject("type");
        ArrayNode typeCoding = type.putArray("coding");
        ObjectNode typeCode = typeCoding.addObject();
        typeCode.put("system", "http://terminology.hl7.org/CodeSystem/library-type");
        typeCode.put("code", "logic-library");
        typeCode.put("display", "Logic Library");

        if (library.getDescription() != null) {
            fhirLibrary.put("description", library.getDescription());
        }

        if (library.getUpdatedAt() != null) {
            fhirLibrary.put("date", library.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        // Content attachments
        ArrayNode content = fhirLibrary.putArray("content");

        // CQL source
        ObjectNode cqlAttachment = content.addObject();
        cqlAttachment.put("contentType", "text/cql");
        cqlAttachment.put("data", Base64.getEncoder().encodeToString(
                library.getCqlContent().getBytes(StandardCharsets.UTF_8)));

        // ELM JSON
        if (library.getElmJson() != null) {
            ObjectNode elmAttachment = content.addObject();
            elmAttachment.put("contentType", "application/elm+json");
            elmAttachment.put("data", Base64.getEncoder().encodeToString(
                    library.getElmJson().getBytes(StandardCharsets.UTF_8)));
        }

        // Dependencies as relatedArtifact
        if (library.getDependencies() != null && !library.getDependencies().isEmpty()) {
            ArrayNode relatedArtifact = fhirLibrary.putArray("relatedArtifact");
            for (String dep : library.getDependencies()) {
                ObjectNode artifact = relatedArtifact.addObject();
                artifact.put("type", "depends-on");
                artifact.put("display", dep);
            }
        }

        return fhirLibrary;
    }

    public CqlLibrary importFhirLibrary(JsonNode fhirLibraryJson) {
        String resourceType = fhirLibraryJson.path("resourceType").asText("");
        if (!"Library".equals(resourceType)) {
            throw new IllegalArgumentException("Expected FHIR Library resource, got: " + resourceType);
        }

        // Extract CQL content from attachments
        String cqlContent = null;
        JsonNode content = fhirLibraryJson.path("content");
        if (content.isArray()) {
            for (JsonNode attachment : content) {
                String contentType = attachment.path("contentType").asText("");
                if ("text/cql".equals(contentType)) {
                    String data = attachment.path("data").asText("");
                    if (!data.isEmpty()) {
                        cqlContent = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
                    }
                    break;
                }
            }
        }

        if (cqlContent == null || cqlContent.isBlank()) {
            throw new IllegalArgumentException("FHIR Library does not contain a text/cql attachment");
        }

        String description = fhirLibraryJson.path("description").asText(null);

        return libraryService.saveLibrary(cqlContent, description);
    }
}
