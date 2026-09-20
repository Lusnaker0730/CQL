package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.fhir.FhirImplementationGuideService;
import com.cqlplatform.service.fhir.VsacService;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.Include;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.LibrarySource;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ValueSetRef;
import com.cqlplatform.service.measure.FhirMeasureService.MeasureExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Assembles the package another installation needs to run a measure (PAT-229): the Measure, its
 * primary Library (CQL + ELM), every included Library, and the value sets the logic uses —
 * as full definitions when this installation can resolve them, otherwise as a flagged stub.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirMeasureBundleService {

    private final FhirMeasureService fhirMeasureService;
    private final CqlLibraryService cqlLibraryService;
    private final CqlTranslationService translationService;
    private final MeasureDefinitionService definitionService;
    private final CqfmLibraryBuilder libraryBuilder;
    private final FhirCanonicalResolver canonical;
    private final FhirContext fhirContext;
    private final VsacService vsacService;
    /** Absent when implementation guides are disabled. */
    private final ObjectProvider<FhirImplementationGuideService> igServiceProvider;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A bundle together with the conformance report of exactly that bundle. */
    public record BundleExport(ObjectNode bundle, MeasureExportConformance conformance) {}

    public ObjectNode exportAsBundle(Long measureId) {
        return exportWithConformance(measureId).bundle();
    }

    public MeasureExportConformance exportConformance(Long measureId) {
        return exportWithConformance(measureId).conformance();
    }

    public BundleExport exportWithConformance(Long measureId) {
        MeasureExport export = fhirMeasureService.export(measureId);
        MeasureDefinition definition = export.definition();
        MeasureExportConformance report = export.conformance();

        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "bundle-" + FhirCanonicalResolver.idPart(definition.getName()));
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());
        ObjectNode identifier = bundle.putObject("identifier");
        identifier.put("system", "urn:ietf:rfc:3986");
        identifier.put("value", "urn:uuid:" + UUID.randomUUID());
        ArrayNode entries = bundle.putArray("entry");

        addEntry(entries, export.measure());

        if (export.libraryName() != null) {
            ObjectNode primary = libraryBuilder.buildLogicLibrary(
                    new LibrarySource(export.libraryName(), export.libraryVersion(), definition.getTitle(),
                            definition.getDescription(), CqfmMeasureBuilder.fhirStatus(definition.getStatus()),
                            definition.getUpdatedAt() != null
                                    ? definition.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : null,
                            definition.getCqlContent(), export.elmJson()),
                    export.dependencies(), export.dataRequirements());
            addEntry(entries, primary);
            for (JsonNode profile : primary.path("meta").path("profile")) {
                report.getLibraryProfiles().add(profile.asText());
            }

            // Included libraries, walked through their own ELM so a transitive include is not lost.
            Set<String> visited = new HashSet<>(Set.of(export.libraryName()));
            Map<String, ValueSetRef> valueSets = new LinkedHashMap<>();
            export.dependencies().valueSets().forEach(vs -> valueSets.putIfAbsent(vs.url(), vs));
            for (Include include : export.dependencies().includes()) {
                collectIncludedLibrary(include, entries, visited, valueSets, report);
            }

            for (ValueSetRef vs : valueSets.values()) {
                addValueSet(entries, vs, report);
            }
        }
        return new BundleExport(bundle, report);
    }

    /** Real FHIR XML: the JSON bundle parsed and re-encoded by HAPI. */
    public String exportAsBundleXml(Long measureId) {
        ObjectNode bundle = exportAsBundle(measureId);
        Bundle parsed = fhirContext.newJsonParser().parseResource(Bundle.class, bundle.toString());
        return fhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(parsed);
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

    private void collectIncludedLibrary(Include include, ArrayNode entries, Set<String> visited,
                                        Map<String, ValueSetRef> valueSets, MeasureExportConformance report) {
        String name = include.path() != null ? include.path() : include.localIdentifier();
        if (name == null || !visited.add(name)) return;

        Optional<CqlLibrary> found = include.version() != null
                ? cqlLibraryService.getLibraryByNameAndVersion(name, include.version())
                        .or(() -> cqlLibraryService.getLatestLibrary(name))
                : cqlLibraryService.getLatestLibrary(name);
        if (found.isEmpty()) {
            // FHIRHelpers is published by HL7 and resolvable by its canonical URL everywhere.
            if (!"FHIRHelpers".equals(name)) {
                report.add(MeasureExportConformance.WARNING, "Bundle.entry",
                        "Included library '" + name + "' is not stored here and is not in the package; "
                                + "the receiver must already have it.");
            }
            return;
        }
        CqlLibrary lib = found.get();
        ElmDependencies deps = libraryBuilder.readDependencies(lib.getElmJson());
        addEntry(entries, libraryBuilder.buildLogicLibrary(
                new LibrarySource(lib.getName(), lib.getVersion(), null, lib.getDescription(), "active",
                        lib.getUpdatedAt() != null ? lib.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : null,
                        lib.getCqlContent(), lib.getElmJson()),
                deps, List.of()));
        deps.valueSets().forEach(vs -> valueSets.putIfAbsent(vs.url(), vs));
        for (Include nested : deps.includes()) {
            collectIncludedLibrary(nested, entries, visited, valueSets, report);
        }
    }

    /**
     * Adds the full value set when it can be resolved (loaded implementation guides first, then
     * VSAC for NLM URLs); otherwise a stub, and the report says the package is not self-contained.
     */
    private void addValueSet(ArrayNode entries, ValueSetRef ref, MeasureExportConformance report) {
        ValueSet resolved = null;
        String source = "none";
        FhirImplementationGuideService igService = igServiceProvider.getIfAvailable();
        try {
            if (igService != null && igService.isLoaded()) {
                resolved = igService.getValueSetByUrl(ref.url());
                if (resolved != null) source = "ig";
            }
            if (resolved == null && vsacService.isConfigured() && ref.url().contains("cts.nlm.nih.gov")) {
                resolved = vsacService.expandValueSetByOid(ref.url().substring(ref.url().lastIndexOf('/') + 1));
                if (resolved != null) source = "vsac";
            }
        } catch (Exception e) {
            log.warn("Could not resolve value set {} for packaging: {}", ref.url(), e.getMessage());
            resolved = null;
            source = "none";
        }

        if (resolved != null) {
            try {
                ObjectNode json = (ObjectNode) MAPPER.readTree(
                        fhirContext.newJsonParser().encodeResourceToString(resolved));
                if (!json.hasNonNull("url")) json.put("url", ref.url());
                addEntry(entries, json);
                report.getValueSets().add(new MeasureExportConformance.ValueSetStatus(ref.url(), ref.name(), true, source));
                return;
            } catch (Exception e) {
                log.warn("Could not serialize value set {}: {}", ref.url(), e.getMessage());
            }
        }

        ObjectNode stub = MAPPER.createObjectNode();
        stub.put("resourceType", "ValueSet");
        stub.put("id", FhirCanonicalResolver.idPart(ref.url().substring(ref.url().lastIndexOf('/') + 1)));
        stub.put("url", ref.url());
        if (ref.name() != null) stub.put("title", ref.name());
        stub.put("status", "unknown");
        addEntry(entries, stub);
        report.getValueSets().add(new MeasureExportConformance.ValueSetStatus(ref.url(), ref.name(), false, "none"));
        report.add(MeasureExportConformance.WARNING, "ValueSet",
                "Value set '" + (ref.name() != null ? ref.name() : ref.url()) + "' (" + ref.url()
                        + ") could not be resolved here. The package only names it, so the receiver needs its own copy.");
    }

    /** fullUrl is the resource's canonical URL when it has one — that is how a receiver matches references. */
    private void addEntry(ArrayNode entries, ObjectNode resource) {
        ObjectNode entry = entries.addObject();
        String url = resource.path("url").asText("");
        entry.put("fullUrl", !url.isBlank() ? url
                : canonical.getBase() + "/" + resource.path("resourceType").asText("Resource")
                        + "/" + resource.path("id").asText(UUID.randomUUID().toString()));
        entry.set("resource", resource);
    }
}
