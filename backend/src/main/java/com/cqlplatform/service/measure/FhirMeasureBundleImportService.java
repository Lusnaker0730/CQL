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
    private final com.cqlplatform.service.terminology.PlatformValueSetService platformValueSets;

    /**
     * {@code valueSetsImported} were stored as this tenant's own (draft) value sets;
     * {@code valueSetsSkipped} either exist here already (same url + version — local content is
     * never overwritten) or came without codes; {@code warnings} says which and why.
     */
    public record BundleImportResult(
            MeasureDefinition measure,
            int librariesImported,
            int librariesSkipped,
            int valueSetsFound,
            int valueSetsImported,
            int valueSetsSkipped,
            List<String> warnings
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
        List<JsonNode> valueSetResources = new ArrayList<>();
        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            switch (resource.path("resourceType").asText("")) {
                case "Measure" -> {
                    if (measureResource == null) measureResource = resource;
                }
                case "Library" -> libraryResources.add(resource);
                case "ValueSet" -> valueSetResources.add(resource);
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

        // PAT-230: the package's value sets become this tenant's own, so the measure resolves the
        // codes its author meant. Before, they were counted and thrown away.
        int valueSetsImported = 0;
        int valueSetsSkipped = 0;
        List<String> warnings = new ArrayList<>();
        for (JsonNode vsJson : valueSetResources) {
            String vsUrl = vsJson.path("url").asText("(no url)");
            try {
                var outcome = platformValueSets.importFhirIfAbsent(vsJson);
                if (outcome.created()) {
                    valueSetsImported++;
                } else {
                    valueSetsSkipped++;
                    warnings.add("Value set " + vsUrl + " version " + outcome.valueSet().getVersion()
                            + " already exists here and was kept as it is — compare its codes with the sender's.");
                }
            } catch (com.cqlplatform.exception.ValidationException e) {
                valueSetsSkipped++;
                warnings.add("Value set " + vsUrl + " was not imported: "
                        + (e.getDetails() != null && !e.getDetails().isEmpty() ? String.join("; ", e.getDetails()) : e.getMessage()));
            }
        }

        String cql = primaryLibrary != null ? cqlOf(primaryLibrary) : null;
        MeasureDefinition importedMeasure = fhirMeasureService.importFhirMeasure(measureResource, cql);
        log.info("Imported measure: {} v{} ({} CQL)", importedMeasure.getName(), importedMeasure.getVersion(),
                cql != null ? "with" : "WITHOUT");

        return new BundleImportResult(importedMeasure, librariesImported, librariesSkipped, valueSetResources.size(),
                valueSetsImported, valueSetsSkipped, warnings);
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
