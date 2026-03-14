package com.cqlplatform.service.authoring;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.ArtifactResponse;
import com.cqlplatform.model.fhir.FhirCodeSystemConstants;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactExportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CqlGenerationService cqlGenerationService;
    private final CqlTranslationService translationService;

    private String fhirHelpersCql;

    @PostConstruct
    void init() {
        try {
            ClassPathResource resource = new ClassPathResource("cql/FHIRHelpers-4.0.1.cql");
            fhirHelpersCql = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("FHIRHelpers-4.0.1.cql not found in classpath", e);
        }
    }

    public byte[] exportAsZip(ArtifactResponse artifact) {
        String cql = cqlGenerationService.generateCql(artifact.getId());

        CqlTranslationRequest translationRequest = new CqlTranslationRequest();
        translationRequest.setCql(cql);
        CqlTranslationResponse translation = translationService.translate(translationRequest);
        if (!translation.isSuccess()) {
            throw new IllegalStateException("CQL translation failed — cannot export ZIP");
        }
        String elmJson = translation.getElmJson();

        String libraryJson = buildCpgLibraryJson(artifact, cql, elmJson);
        String safeName = artifact.getName().replaceAll("[^a-zA-Z0-9_-]", "_");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            addZipEntry(zos, safeName + ".cql", cql.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, safeName + "-elm.json", elmJson.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, "FHIRHelpers-4.0.1.cql", fhirHelpersCql.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zos, safeName + "-library.json", libraryJson.getBytes(StandardCharsets.UTF_8));

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create ZIP archive", e);
        }
    }

    private String buildCpgLibraryJson(ArtifactResponse artifact, String cql, String elmJson) {
        ObjectNode library = MAPPER.createObjectNode();
        library.put("resourceType", "Library");
        library.put("id", artifact.getName().replaceAll("[^a-zA-Z0-9_-]", "_"));
        library.put("name", artifact.getName());
        library.put("version", artifact.getVersion() != null ? artifact.getVersion() : "1.0.0");
        library.put("status", artifact.getStatus() != null ? artifact.getStatus().toLowerCase() : "draft");

        // Type: logic-library
        ObjectNode type = library.putObject("type");
        ArrayNode typeCoding = type.putArray("coding");
        ObjectNode typeCode = typeCoding.addObject();
        typeCode.put("system", FhirCodeSystemConstants.CS_LIBRARY_TYPE);
        typeCode.put("code", "logic-library");

        // CPG metadata
        if (artifact.getUrl() != null) library.put("url", artifact.getUrl());
        if (artifact.getPublisher() != null) library.put("publisher", artifact.getPublisher());
        if (artifact.getPurpose() != null) library.put("purpose", artifact.getPurpose());
        if (artifact.getCopyright() != null) library.put("copyright", artifact.getCopyright());
        if (artifact.getDescription() != null) library.put("description", artifact.getDescription());
        if (artifact.getExperimental() != null) library.put("experimental", artifact.getExperimental());
        if (artifact.getApprovalDate() != null) library.put("approvalDate", artifact.getApprovalDate().toString());
        if (artifact.getLastReviewDate() != null) library.put("lastReviewDate", artifact.getLastReviewDate().toString());

        if (artifact.getEffectivePeriodStart() != null || artifact.getEffectivePeriodEnd() != null) {
            ObjectNode period = library.putObject("effectivePeriod");
            if (artifact.getEffectivePeriodStart() != null) period.put("start", artifact.getEffectivePeriodStart().toString());
            if (artifact.getEffectivePeriodEnd() != null) period.put("end", artifact.getEffectivePeriodEnd().toString());
        }

        addContactArray(library, "author", artifact.getAuthor());
        addContactArray(library, "reviewer", artifact.getReviewer());
        addContactArray(library, "endorser", artifact.getEndorser());
        addRelatedArtifacts(library, artifact.getRelatedArtifact());

        // Content attachments
        ArrayNode content = library.putArray("content");

        ObjectNode cqlAttachment = content.addObject();
        cqlAttachment.put("contentType", "text/cql");
        cqlAttachment.put("data", Base64.getEncoder().encodeToString(cql.getBytes(StandardCharsets.UTF_8)));

        ObjectNode elmAttachment = content.addObject();
        elmAttachment.put("contentType", "application/elm+json");
        elmAttachment.put("data", Base64.getEncoder().encodeToString(elmJson.getBytes(StandardCharsets.UTF_8)));

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(library);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize Library JSON", e);
        }
    }

    private void addContactArray(ObjectNode parent, String fieldName, List<Map<String, Object>> contacts) {
        if (contacts == null || contacts.isEmpty()) return;
        ArrayNode arr = parent.putArray(fieldName);
        for (Map<String, Object> contact : contacts) {
            ObjectNode node = arr.addObject();
            Object name = contact.get("name");
            if (name != null) node.put("name", name.toString());
        }
    }

    private void addRelatedArtifacts(ObjectNode parent, List<Map<String, Object>> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) return;
        ArrayNode arr = parent.putArray("relatedArtifact");
        for (Map<String, Object> ra : artifacts) {
            ObjectNode node = arr.addObject();
            Object type = ra.get("type");
            if (type != null) node.put("type", type.toString());
            Object display = ra.get("display");
            if (display != null) node.put("display", display.toString());
            Object url = ra.get("url");
            if (url != null) node.put("url", url.toString());
        }
    }

    private void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
