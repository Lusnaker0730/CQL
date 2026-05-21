package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "fhir.implementation-guides.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FhirImplementationGuideService {

    private final FhirContext fhirContext;

    @Getter
    private NpmPackageValidationSupport validationSupport;

    private final Map<String, StructureDefinition> profilesByUrl = new LinkedHashMap<>();
    private final Map<String, ValueSet> valueSetsByUrl = new LinkedHashMap<>();
    private final Map<String, CodeSystem> codeSystemsByUrl = new LinkedHashMap<>();

    @Getter
    private PackageMetadata packageMetadata;

    @Value("${fhir.implementation-guides.package-path:classpath:fhir-packages/twcore/package.tgz}")
    private String packagePath;

    public FhirImplementationGuideService(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    @PostConstruct
    public void init() {
        try {
            validationSupport = new NpmPackageValidationSupport(fhirContext);
            String classpathResource = packagePath.replace("classpath:", "");
            validationSupport.loadPackageFromClasspath(classpathResource);
            log.info("Loaded IG package from: {}", packagePath);

            indexResources();

            packageMetadata = new PackageMetadata(
                    "tw.gov.mohw.twcore",
                    "0.3.2",
                    "TW Core IG (臺灣核心實作指引)",
                    "https://twcore.mohw.gov.tw/ig/twcore",
                    "4.0.1",
                    profilesByUrl.size(),
                    valueSetsByUrl.size(),
                    codeSystemsByUrl.size()
            );

            log.info("Loaded TW Core IG: {} v{} ({} profiles, {} valuesets, {} codesystems)",
                    packageMetadata.name(), packageMetadata.version(),
                    packageMetadata.profileCount(), packageMetadata.valueSetCount(),
                    packageMetadata.codeSystemCount());

        } catch (IOException e) {
            log.error("Failed to load IG package from {}: {}", packagePath, e.getMessage(), e);
            validationSupport = null;
            packageMetadata = null;
        }
    }

    private void indexResources() {
        // Fetch all StructureDefinitions
        List<?> structDefs = validationSupport.fetchAllStructureDefinitions();
        if (structDefs != null) {
            for (Object sd : structDefs) {
                if (sd instanceof StructureDefinition structDef) {
                    if (structDef.getUrl() != null) {
                        profilesByUrl.put(structDef.getUrl(), structDef);
                    }
                }
            }
        }

        // Fetch all ValueSets - we need to check if they can be fetched
        // NpmPackageValidationSupport stores them internally; we access via fetchValueSet for known URLs
        // Instead, we iterate through what's available via the conformance resources
        List<?> conformanceResources = validationSupport.fetchAllConformanceResources();
        if (conformanceResources != null) {
            for (Object resource : conformanceResources) {
                if (resource instanceof ValueSet vs && vs.getUrl() != null) {
                    valueSetsByUrl.put(vs.getUrl(), vs);
                } else if (resource instanceof CodeSystem cs && cs.getUrl() != null) {
                    codeSystemsByUrl.put(cs.getUrl(), cs);
                } else if (resource instanceof StructureDefinition sd && sd.getUrl() != null) {
                    profilesByUrl.putIfAbsent(sd.getUrl(), sd);
                }
            }
        }
    }

    public boolean isLoaded() {
        return validationSupport != null && packageMetadata != null;
    }

    public List<ProfileSummary> getProfiles(String resourceType, String search) {
        return profilesByUrl.values().stream()
                .filter(sd -> resourceType == null || resourceType.isBlank()
                        || resourceType.equals(sd.getType()))
                .filter(sd -> search == null || search.isBlank()
                        || matchesSearch(sd.getName(), sd.getTitle(), sd.getUrl(), search))
                .map(sd -> new ProfileSummary(
                        sd.getUrl(),
                        sd.getName(),
                        sd.getTitle(),
                        sd.getType(),
                        sd.getKind() != null ? sd.getKind().toCode() : null,
                        sd.getStatus() != null ? sd.getStatus().toCode() : null
                ))
                .collect(Collectors.toList());
    }

    public StructureDefinition getProfileByUrl(String url) {
        return profilesByUrl.get(url);
    }

    public List<ValueSetSummary> getValueSets(String search) {
        return valueSetsByUrl.values().stream()
                .filter(vs -> search == null || search.isBlank()
                        || matchesSearch(vs.getName(), vs.getTitle(), vs.getUrl(), search))
                .map(vs -> new ValueSetSummary(
                        vs.getUrl(),
                        vs.getName(),
                        vs.getTitle(),
                        vs.getStatus() != null ? vs.getStatus().toCode() : null,
                        countValueSetConcepts(vs),
                        hasComposeRules(vs)
                ))
                .collect(Collectors.toList());
    }

    public ValueSet getValueSetByUrl(String url) {
        return valueSetsByUrl.get(url);
    }

    public List<CodeSystemSummary> getCodeSystems(String search) {
        return codeSystemsByUrl.values().stream()
                .filter(cs -> search == null || search.isBlank()
                        || matchesSearch(cs.getName(), cs.getTitle(), cs.getUrl(), search))
                .map(cs -> new CodeSystemSummary(
                        cs.getUrl(),
                        cs.getName(),
                        cs.getTitle(),
                        cs.getStatus() != null ? cs.getStatus().toCode() : null,
                        cs.getConcept() != null ? cs.getConcept().size() : 0
                ))
                .collect(Collectors.toList());
    }

    public CodeSystem getCodeSystemByUrl(String url) {
        return codeSystemsByUrl.get(url);
    }

    /**
     * Get recommended TW Core profiles for a given FHIR resource type.
     * Only returns profiles that are constraints (not base definitions).
     */
    public List<ProfileSummary> getRecommendedProfiles(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return List.of();
        }
        return profilesByUrl.values().stream()
                .filter(sd -> resourceType.equals(sd.getType()))
                .filter(sd -> sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT)
                .map(sd -> new ProfileSummary(
                        sd.getUrl(),
                        sd.getName(),
                        sd.getTitle(),
                        sd.getType(),
                        sd.getKind() != null ? sd.getKind().toCode() : null,
                        sd.getStatus() != null ? sd.getStatus().toCode() : null
                ))
                .collect(Collectors.toList());
    }

    /**
     * Attempt to find the best matching TW Core profile URL for a given resource type.
     * Returns the first constraint profile found, or null if none exists.
     */
    public String findProfileUrlForResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        return profilesByUrl.values().stream()
                .filter(sd -> resourceType.equals(sd.getType()))
                .filter(sd -> sd.getDerivation() == StructureDefinition.TypeDerivationRule.CONSTRAINT)
                .map(StructureDefinition::getUrl)
                .findFirst()
                .orElse(null);
    }

    private boolean matchesSearch(String name, String title, String url, String search) {
        String lowerSearch = search.toLowerCase();
        return (name != null && name.toLowerCase().contains(lowerSearch))
                || (title != null && title.toLowerCase().contains(lowerSearch))
                || (url != null && url.toLowerCase().contains(lowerSearch));
    }

    private int countValueSetConcepts(ValueSet vs) {
        if (vs.hasExpansion() && vs.getExpansion().hasContains()) {
            return vs.getExpansion().getContains().size();
        }
        if (vs.hasCompose()) {
            return vs.getCompose().getInclude().stream()
                    .mapToInt(inc -> inc.hasConcept() ? inc.getConcept().size() : 0)
                    .sum();
        }
        return 0;
    }

    /**
     * PAT-119: many TWCORE ValueSets use {@code compose.include.filter} (e.g.
     * "all SNOMED CT descendants of X") or {@code compose.include.valueSet}
     * (reference another VS) instead of inline {@code concept} lists. Previously
     * the UI just showed "0 concepts" which read as "empty / broken"; callers
     * now use this flag to render a hint that the VS needs terminology-server
     * expansion to enumerate actual codes.
     */
    private boolean hasComposeRules(ValueSet vs) {
        if (!vs.hasCompose()) return false;
        for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
            if (include.hasFilter() && !include.getFilter().isEmpty()) return true;
            if (include.hasValueSet() && !include.getValueSet().isEmpty()) return true;
        }
        return false;
    }

    // DTOs
    public record PackageMetadata(
            String name,
            String version,
            String title,
            String canonical,
            String fhirVersion,
            int profileCount,
            int valueSetCount,
            int codeSystemCount
    ) {}

    public record ProfileSummary(
            String url,
            String name,
            String title,
            String type,
            String kind,
            String status
    ) {}

    public record ValueSetSummary(
            String url,
            String name,
            String title,
            String status,
            int conceptCount,
            /** PAT-119: true when the ValueSet uses compose.include.filter or
             *  compose.include.valueSet (needs terminology-server expansion to
             *  enumerate). Frontend renders a hint chip instead of "0". */
            boolean hasComposeRules
    ) {}

    public record CodeSystemSummary(
            String url,
            String name,
            String title,
            String status,
            int conceptCount
    ) {}
}
