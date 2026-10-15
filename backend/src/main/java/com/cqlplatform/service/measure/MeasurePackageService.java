package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.service.measure.FhirMeasureBundleService.BundleExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The exchange package as one zip (PAT-261, MADiE gap item 10) — laid out the way MADiE's
 * packaging utility lays out a FHIR measure export, so a receiver tooled for MADiE packages can
 * open ours:
 *
 * <pre>
 * {@code <Title>-v<version>-FHIR.json}      the complete Bundle (Measure + Libraries + ValueSets)
 * {@code <Title>-v<version>-FHIR.xml}       the same Bundle as FHIR XML
 * {@code <Title>-v<version>-FHIR.html}      the human-readable rendering
 * {@code conformance.json}                  this installation's conformance report of the package
 * {@code cql/<Library>-<version>.cql}       every library's CQL as a plain file
 * {@code resources/measure-<name>-<version>.{json,xml}}
 * {@code resources/library-<name>-<version>.{json,xml}}
 * {@code resources/valueset-<id>-<version>.json}   (value sets travel inside our packages; MADiE's rely on VSAC)
 * </pre>
 *
 * {@code <Title>} is the eCQM abbreviated title when the measure has one, else its name — the
 * same choice MADiE makes ({@code ecqmTitle-v<version>-FHIR}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurePackageService {

    static final String CQL_DIRECTORY = "cql/";
    static final String RESOURCES_DIRECTORY = "resources/";
    static final String CONFORMANCE_ENTRY = "conformance.json";
    private static final String MODEL_SUFFIX = "-FHIR";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FhirMeasureBundleService bundleService;
    private final HumanReadableService humanReadableService;
    private final FhirContext fhirContext;

    /** One package: the zip bytes, the file name to hand out (without {@code .zip}) and the report of what is inside. */
    public record MeasurePackage(String fileName, byte[] zip, MeasureExportConformance conformance) {}

    public MeasurePackage exportPackage(Long measureId) {
        BundleExport export = bundleService.exportWithConformance(measureId);
        MeasureDefinition definition = export.definition();
        String base = baseName(definition);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(base + ".json", pretty(export.bundle()));
        entries.put(base + ".xml", xml(export.bundle()));
        entries.put(base + ".html", humanReadableService.generateHtml(definition).getBytes(StandardCharsets.UTF_8));
        entries.put(CONFORMANCE_ENTRY, pretty(MAPPER.valueToTree(export.conformance())));

        for (JsonNode entry : export.bundle().path("entry")) {
            JsonNode resource = entry.path("resource");
            String type = resource.path("resourceType").asText("");
            String version = resource.path("version").asText("1.0.0");
            switch (type) {
                case "Measure" -> {
                    String file = RESOURCES_DIRECTORY + "measure-" + safe(resource.path("name").asText(base)) + "-" + safe(version);
                    entries.put(file + ".json", pretty(resource));
                    entries.put(file + ".xml", xml(resource));
                }
                case "Library" -> {
                    String name = safe(resource.path("name").asText("Library"));
                    String file = RESOURCES_DIRECTORY + "library-" + name + "-" + safe(version);
                    entries.put(file + ".json", pretty(resource));
                    entries.put(file + ".xml", xml(resource));
                    String cql = cqlOf(resource);
                    if (cql != null) entries.put(CQL_DIRECTORY + name + "-" + safe(version) + ".cql", cql.getBytes(StandardCharsets.UTF_8));
                }
                case "ValueSet" -> {
                    String id = resource.path("id").asText("valueset");
                    String file = RESOURCES_DIRECTORY + "valueset-" + safe(id)
                            + (resource.hasNonNull("version") ? "-" + safe(version) : "");
                    entries.put(file + ".json", pretty(resource));
                }
                default -> { /* nothing else is packaged */ }
            }
        }
        log.info("Packaged measure {} as {}.zip ({} entries)", measureId, base, entries.size());
        return new MeasurePackage(base, zip(entries), export.conformance());
    }

    /** {@code <ecqmTitle or name>-v<version>-FHIR}, restricted to characters every file system accepts. */
    static String baseName(MeasureDefinition definition) {
        String title = definition.getEcqmTitle() != null && !definition.getEcqmTitle().isBlank()
                ? definition.getEcqmTitle().trim() : definition.getName();
        String version = definition.getVersion() != null && !definition.getVersion().isBlank() ? definition.getVersion() : "1.0.0";
        return safe(title) + "-v" + safe(version) + MODEL_SUFFIX;
    }

    static String safe(String s) {
        String cleaned = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return cleaned.isEmpty() ? "measure" : cleaned;
    }

    private static String cqlOf(JsonNode library) {
        for (JsonNode attachment : library.path("content")) {
            if ("text/cql".equals(attachment.path("contentType").asText("")) && attachment.hasNonNull("data")) {
                return new String(Base64.getDecoder().decode(attachment.get("data").asText()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private byte[] xml(JsonNode resource) {
        IBaseResource parsed = fhirContext.newJsonParser().parseResource(resource.toString());
        return fhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(parsed).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pretty(JsonNode node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] zip(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** True when the upload looks like a zip (by name or by the {@code PK} signature). */
    static boolean looksLikeZip(byte[] content, String fileName) {
        return (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip"))
                || (content != null && content.length > 4 && content[0] == 'P' && content[1] == 'K');
    }
}
