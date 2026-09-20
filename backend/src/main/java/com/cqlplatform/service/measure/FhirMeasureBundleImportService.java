package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Imports a measure package: the Measure, the Libraries it needs, and — the part that makes the
 * result usable — the CQL of the measure's primary library.
 *
 * <p>PAT-229: the import used to create a measure with populations but no CQL (the logic lived
 * only in the imported Library rows), so a package exported by one installation could not be
 * evaluated after being imported by another.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirMeasureBundleImportService {

    private final FhirMeasureService fhirMeasureService;
    private final FhirLibraryService fhirLibraryService;
    private final CqlLibraryService cqlLibraryService;

    public record BundleImportResult(
            MeasureDefinition measure,
            int librariesImported,
            int librariesSkipped,
            int valueSetsFound
    ) {}

    public BundleImportResult importBundle(JsonNode bundleJson) {
        String resourceType = bundleJson.path("resourceType").asText("");
        if (!"Bundle".equals(resourceType)) {
            throw new IllegalArgumentException("Expected FHIR Bundle resource, got: " + resourceType);
        }

        JsonNode entries = bundleJson.path("entry");
        if (!entries.isArray() || entries.isEmpty()) {
            throw new IllegalArgumentException("Bundle contains no entries");
        }

        JsonNode measureResource = null;
        List<JsonNode> libraryResources = new ArrayList<>();
        int valueSetsFound = 0;
        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            switch (resource.path("resourceType").asText("")) {
                case "Measure" -> {
                    if (measureResource == null) measureResource = resource;
                }
                case "Library" -> libraryResources.add(resource);
                case "ValueSet" -> valueSetsFound++;
                default -> { /* not part of a measure package */ }
            }
        }
        if (measureResource == null) {
            throw new IllegalArgumentException("Bundle does not contain a Measure resource");
        }

        JsonNode primaryLibrary = findPrimaryLibrary(measureResource, libraryResources);

        // Dependencies first: the measure's CQL is compiled when the measure is created, and its
        // includes have to be resolvable by then. The primary library is not stored as a shared
        // library — its CQL becomes the measure's own logic.
        int librariesImported = 0;
        int librariesSkipped = 0;
        for (JsonNode libJson : libraryResources) {
            if (libJson == primaryLibrary) continue;
            String name = libJson.path("name").asText("");
            String version = libJson.path("version").asText("1.0.0");
            if (cqlLibraryService.getLibraryByNameAndVersion(name, version).isPresent()) {
                librariesSkipped++;
                log.info("Skipping existing library: {} v{}", name, version);
                continue;
            }
            try {
                fhirLibraryService.importFhirLibrary(libJson);
                librariesImported++;
                log.info("Imported library: {} v{}", name, version);
            } catch (Exception e) {
                log.warn("Failed to import library {} v{}: {}", name, version, e.getMessage());
                librariesSkipped++;
            }
        }

        String cql = primaryLibrary != null ? cqlOf(primaryLibrary) : null;
        MeasureDefinition importedMeasure = fhirMeasureService.importFhirMeasure(measureResource, cql);
        log.info("Imported measure: {} v{} ({} CQL)", importedMeasure.getName(), importedMeasure.getVersion(),
                cql != null ? "with" : "WITHOUT");

        return new BundleImportResult(importedMeasure, librariesImported, librariesSkipped, valueSetsFound);
    }

    /**
     * The library {@code Measure.library} points at, matched by canonical URL and then by name;
     * a package with a single library and no reference (older exports) uses that library.
     */
    private JsonNode findPrimaryLibrary(JsonNode measure, List<JsonNode> libraries) {
        JsonNode reference = measure.path("library");
        if (reference.isArray() && !reference.isEmpty()) {
            String canonical = reference.get(0).asText("");
            String url = canonical.contains("|") ? canonical.substring(0, canonical.indexOf('|')) : canonical;
            String name = FhirMeasureService.libraryNameOf(canonical);
            for (JsonNode lib : libraries) {
                if (!url.isBlank() && url.equals(lib.path("url").asText(null))) return lib;
            }
            for (JsonNode lib : libraries) {
                if (name != null && (name.equals(lib.path("name").asText(null)) || name.equals(lib.path("id").asText(null)))) {
                    return lib;
                }
            }
        }
        return libraries.size() == 1 ? libraries.get(0) : null;
    }

    private String cqlOf(JsonNode library) {
        for (JsonNode attachment : library.path("content")) {
            if ("text/cql".equals(attachment.path("contentType").asText(""))) {
                String data = attachment.path("data").asText("");
                if (!data.isEmpty()) {
                    return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
