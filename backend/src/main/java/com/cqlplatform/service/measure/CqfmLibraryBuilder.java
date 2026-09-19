package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.DataRequirementInfo;
import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.fhir.FhirCodeSystemConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Builds FHIR Library resources for a measure package (PAT-229): the CQL / ELM logic library
 * and the contained "effective data requirements" module-definition library.
 *
 * <p>Dependencies are read from the ELM, not from the translator's display metadata — that
 * metadata only carries value set NAMES, which the previous export wrote out as if they were
 * URLs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CqfmLibraryBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FhirCanonicalResolver canonical;

    /** What a library depends on, as declared in its ELM. */
    public record ElmDependencies(List<Include> includes, List<ValueSetRef> valueSets,
                                  List<CodeSystemRef> codeSystems, List<String> parameters) {
        public static ElmDependencies empty() {
            return new ElmDependencies(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record Include(String localIdentifier, String path, String version) {}

    public record ValueSetRef(String name, String url, String version) {}

    public record CodeSystemRef(String name, String url, String version) {}

    /** Input for {@link #buildLogicLibrary}. */
    public record LibrarySource(String name, String version, String title, String description,
                                String status, String date, String cql, String elmJson) {}

    /**
     * Reads includes / value sets / code systems / parameters from ELM JSON. A library without
     * ELM (translation failed) yields empty dependencies — the caller reports that.
     */
    public ElmDependencies readDependencies(String elmJson) {
        if (elmJson == null || elmJson.isBlank()) return ElmDependencies.empty();
        try {
            JsonNode library = MAPPER.readTree(elmJson).path("library");
            List<Include> includes = new ArrayList<>();
            for (JsonNode def : library.path("includes").path("def")) {
                includes.add(new Include(def.path("localIdentifier").asText(null),
                        def.path("path").asText(null), def.path("version").asText(null)));
            }
            List<ValueSetRef> valueSets = new ArrayList<>();
            for (JsonNode def : library.path("valueSets").path("def")) {
                String url = def.path("id").asText(null);
                if (url != null && !url.isBlank()) {
                    valueSets.add(new ValueSetRef(def.path("name").asText(null), url, def.path("version").asText(null)));
                }
            }
            List<CodeSystemRef> codeSystems = new ArrayList<>();
            for (JsonNode def : library.path("codeSystems").path("def")) {
                String url = def.path("id").asText(null);
                if (url != null && !url.isBlank()) {
                    codeSystems.add(new CodeSystemRef(def.path("name").asText(null), url, def.path("version").asText(null)));
                }
            }
            List<String> parameters = new ArrayList<>();
            for (JsonNode def : library.path("parameters").path("def")) {
                String name = def.path("name").asText(null);
                if (name != null) parameters.add(name);
            }
            return new ElmDependencies(includes, valueSets, codeSystems, parameters);
        } catch (Exception e) {
            log.warn("Could not read dependencies from ELM: {}", e.getMessage());
            return ElmDependencies.empty();
        }
    }

    /** Canonical URL of an included library; FHIRHelpers resolves to the one HL7 publishes. */
    public String includeCanonical(Include include) {
        String name = include.path() != null ? include.path() : include.localIdentifier();
        String url = "FHIRHelpers".equals(name) ? CqfmConstants.FHIR_HELPERS_CANONICAL : canonical.libraryUrl(name);
        return FhirCanonicalResolver.versioned(url, include.version());
    }

    /**
     * A logic library carrying the CQL and, when available, its ELM.
     *
     * @return the resource; the profiles it claims are in {@code meta.profile}
     */
    public ObjectNode buildLogicLibrary(LibrarySource src, ElmDependencies deps,
                                        List<DataRequirementInfo> dataRequirements) {
        ObjectNode library = MAPPER.createObjectNode();
        library.put("resourceType", "Library");
        library.put("id", FhirCanonicalResolver.idPart(src.name()));

        boolean hasElm = src.elmJson() != null && !src.elmJson().isBlank();
        boolean shareable = notBlank(src.title()) && notBlank(src.description());
        ArrayNode profiles = library.putObject("meta").putArray("profile");
        profiles.add(CqfmConstants.PROFILE_CQL_LIBRARY);
        if (hasElm) profiles.add(CqfmConstants.PROFILE_ELM_JSON_LIBRARY);
        if (shareable) profiles.add(CqfmConstants.PROFILE_CRMI_SHAREABLE_LIBRARY);

        library.put("url", canonical.libraryUrl(src.name()));
        library.put("version", notBlank(src.version()) ? src.version() : "1.0.0");
        library.put("name", src.name());
        if (notBlank(src.title())) library.put("title", src.title());
        library.put("status", notBlank(src.status()) ? src.status() : "active");
        if (notBlank(src.date())) library.put("date", src.date());
        if (notBlank(src.description())) library.put("description", src.description());

        codeable(library.putObject("type"), FhirCodeSystemConstants.CS_LIBRARY_TYPE, "logic-library", "Logic Library");

        addRelatedArtifacts(library, deps);
        addParameters(library, deps);
        addDataRequirements(library, dataRequirements);

        ArrayNode content = library.putArray("content");
        ObjectNode cqlAttachment = content.addObject();
        cqlAttachment.put("contentType", "text/cql");
        cqlAttachment.put("data", base64(src.cql()));
        if (hasElm) {
            ObjectNode elmAttachment = content.addObject();
            elmAttachment.put("contentType", "application/elm+json");
            elmAttachment.put("data", base64(src.elmJson()));
        }
        return library;
    }

    /**
     * The contained module-definition Library a Measure points at with
     * {@code crmi-effectiveDataRequirements}: every terminology / library dependency and every
     * data requirement of the measure logic (QM IG conformance requirements 3.5 and 3.6).
     */
    public ObjectNode buildEffectiveDataRequirements(ElmDependencies deps, List<DataRequirementInfo> dataRequirements) {
        ObjectNode library = MAPPER.createObjectNode();
        library.put("resourceType", "Library");
        library.put("id", CqfmConstants.EFFECTIVE_DATA_REQUIREMENTS_ID);
        library.put("status", "active");
        codeable(library.putObject("type"), FhirCodeSystemConstants.CS_LIBRARY_TYPE, "module-definition", "Module Definition");
        addRelatedArtifacts(library, deps);
        addParameters(library, deps);
        addDataRequirements(library, dataRequirements);
        return library;
    }

    private void addRelatedArtifacts(ObjectNode library, ElmDependencies deps) {
        if (deps.includes().isEmpty() && deps.valueSets().isEmpty() && deps.codeSystems().isEmpty()) return;
        ArrayNode related = library.putArray("relatedArtifact");
        for (Include include : deps.includes()) {
            ObjectNode artifact = related.addObject();
            artifact.put("type", "depends-on");
            artifact.put("display", "Library " + (include.path() != null ? include.path() : include.localIdentifier()));
            artifact.put("resource", includeCanonical(include));
        }
        for (CodeSystemRef cs : deps.codeSystems()) {
            ObjectNode artifact = related.addObject();
            artifact.put("type", "depends-on");
            artifact.put("display", "Code system " + cs.name());
            artifact.put("resource", FhirCanonicalResolver.versioned(cs.url(), cs.version()));
        }
        for (ValueSetRef vs : deps.valueSets()) {
            ObjectNode artifact = related.addObject();
            artifact.put("type", "depends-on");
            artifact.put("display", "Value set " + vs.name());
            artifact.put("resource", FhirCanonicalResolver.versioned(vs.url(), vs.version()));
        }
    }

    /**
     * Only the measurement period is declared: it is the one parameter whose FHIR type is known
     * without type inference, and the one every measure engine needs to find.
     */
    private void addParameters(ObjectNode library, ElmDependencies deps) {
        if (!deps.parameters().contains("Measurement Period")) return;
        ObjectNode parameter = library.putArray("parameter").addObject();
        parameter.put("name", "Measurement Period");
        parameter.put("use", "in");
        parameter.put("min", 0);
        parameter.put("max", "1");
        parameter.put("type", "Period");
    }

    void addDataRequirements(ObjectNode target, List<DataRequirementInfo> requirements) {
        if (requirements == null || requirements.isEmpty()) return;
        ArrayNode drArray = target.putArray("dataRequirement");
        for (DataRequirementInfo dr : requirements) {
            ObjectNode drNode = drArray.addObject();
            drNode.put("type", dr.getType());
            if (dr.getProfile() != null && !dr.getProfile().isEmpty()) {
                ArrayNode profileArray = drNode.putArray("profile");
                dr.getProfile().forEach(profileArray::add);
            }
            if (dr.getCodeFilter() != null && !dr.getCodeFilter().isEmpty()) {
                ArrayNode cfArray = drNode.putArray("codeFilter");
                for (DataRequirementInfo.CodeFilterInfo cf : dr.getCodeFilter()) {
                    ObjectNode cfNode = cfArray.addObject();
                    if (cf.getPath() != null) cfNode.put("path", cf.getPath());
                    if (cf.getValueSet() != null) cfNode.put("valueSet", cf.getValueSet());
                    if (cf.getCode() != null && !cf.getCode().isEmpty()) {
                        ArrayNode codeArray = cfNode.putArray("code");
                        for (DataRequirementInfo.CodingInfo coding : cf.getCode()) {
                            ObjectNode codeNode = codeArray.addObject();
                            if (coding.getSystem() != null) codeNode.put("system", coding.getSystem());
                            if (coding.getCode() != null) codeNode.put("code", coding.getCode());
                            if (coding.getDisplay() != null) codeNode.put("display", coding.getDisplay());
                        }
                    }
                }
            }
            if (dr.getDateFilter() != null && !dr.getDateFilter().isEmpty()) {
                ArrayNode dfArray = drNode.putArray("dateFilter");
                for (DataRequirementInfo.DateFilterInfo df : dr.getDateFilter()) {
                    if (df.getPath() != null) dfArray.addObject().put("path", df.getPath());
                }
            }
        }
    }

    static void codeable(ObjectNode target, String system, String code, String display) {
        ObjectNode coding = target.putArray("coding").addObject();
        coding.put("system", system);
        coding.put("code", code);
        if (display != null) coding.put("display", display);
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString((s != null ? s : "").getBytes(StandardCharsets.UTF_8));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
