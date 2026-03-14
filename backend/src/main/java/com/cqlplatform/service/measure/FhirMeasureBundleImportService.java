package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses a FHIR Bundle and imports contained Measure + Library resources.
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

        // Classify resources
        JsonNode measureResource = null;
        java.util.List<JsonNode> libraryResources = new java.util.ArrayList<>();
        int valueSetsFound = 0;

        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            String type = resource.path("resourceType").asText("");
            switch (type) {
                case "Measure" -> {
                    if (measureResource == null) measureResource = resource;
                }
                case "Library" -> libraryResources.add(resource);
                case "ValueSet" -> valueSetsFound++;
            }
        }

        // Import Libraries first (dependencies before main)
        int librariesImported = 0;
        int librariesSkipped = 0;
        for (JsonNode libJson : libraryResources) {
            String name = libJson.path("name").asText("");
            String version = libJson.path("version").asText("1.0.0");
            // Skip if already exists
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

        // Import Measure
        if (measureResource == null) {
            throw new IllegalArgumentException("Bundle does not contain a Measure resource");
        }

        MeasureDefinition importedMeasure = fhirMeasureService.importFhirMeasure(measureResource);
        log.info("Imported measure: {} v{}", importedMeasure.getName(), importedMeasure.getVersion());

        // Link to main library if we imported one
        if (!libraryResources.isEmpty() && importedMeasure.getCqlLibraryId() == null) {
            String mainLibName = libraryResources.get(0).path("name").asText("");
            String mainLibVersion = libraryResources.get(0).path("version").asText("1.0.0");
            cqlLibraryService.getLibraryByNameAndVersion(mainLibName, mainLibVersion).ifPresent(lib ->
                    importedMeasure.setCqlLibraryId(lib.getId())
            );
        }

        return new BundleImportResult(importedMeasure, librariesImported, librariesSkipped, valueSetsFound);
    }
}
