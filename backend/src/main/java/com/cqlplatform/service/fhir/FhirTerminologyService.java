package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.fhir.terminology.R4FhirTerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class FhirTerminologyService {

    private static final List<String> FALLBACK_TERMINOLOGY_SERVERS = List.of(
            "https://tx.fhir.org/r4",
            "https://r4.ontoserver.csiro.au/fhir"
    );

    private static final Map<String, String> IMPLICIT_VALUESET_URLS = Map.of(
            "http://loinc.org", "http://loinc.org/vs",
            "http://snomed.info/sct", "http://snomed.info/sct?fhir_vs",
            "http://hl7.org/fhir/sid/icd-10-cm", "http://hl7.org/fhir/sid/icd-10-cm?fhir_vs",
            "http://www.nlm.nih.gov/research/umls/rxnorm", "http://www.nlm.nih.gov/research/umls/rxnorm?fhir_vs",
            "http://www.ama-assn.org/go/cpt", "http://www.ama-assn.org/go/cpt?fhir_vs"
    );

    private final FhirContext fhirContext;
    private final CacheManager cacheManager;
    private final FhirImplementationGuideService igService;
    private final VsacService vsacService;

    @Value("${fhir.terminology.url:https://tx.fhir.org/r4}")
    private String defaultTerminologyServerUrl;

    public FhirTerminologyService(FhirContext fhirContext,
                                   CacheManager cacheManager,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   FhirImplementationGuideService igService,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   VsacService vsacService) {
        this.fhirContext = fhirContext;
        this.cacheManager = cacheManager;
        this.igService = igService;
        this.vsacService = vsacService;
    }

    public TerminologyProvider createTerminologyProvider(String terminologyServerUrl) {
        String serverUrl = terminologyServerUrl != null ? terminologyServerUrl : defaultTerminologyServerUrl;
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
        TerminologyProvider remoteProvider = new R4FhirTerminologyProvider(client);

        boolean hasIg = igService != null && igService.isLoaded();
        boolean hasVsac = vsacService != null;

        if (hasIg || hasVsac) {
            log.debug("Creating terminology provider with local IG={}, VSAC={}", hasIg, hasVsac);
            return new LocalTerminologyProvider(hasIg ? igService : null, remoteProvider, hasVsac ? vsacService : null);
        }
        return remoteProvider;
    }

    @Cacheable(value = "valueSets", key = "#valueSetUrl")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "expandValueSetFallback")
    @Retry(name = "fhirTerminology")
    public ValueSet expandValueSet(String valueSetUrl, String filter) {
        log.debug("Expanding ValueSet: {}", valueSetUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(defaultTerminologyServerUrl);

        try {
            Parameters params = new Parameters();
            params.addParameter("url", new UriType(valueSetUrl));
            if (filter != null && !filter.isBlank()) {
                params.addParameter("filter", new StringType(filter));
            }

            return client.operation()
                    .onType(ValueSet.class)
                    .named("$expand")
                    .withParameters(params)
                    .returnResourceType(ValueSet.class)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to expand ValueSet: {}", valueSetUrl, e);
            throw new FhirServerUnavailableException("ValueSet expansion failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private ValueSet expandValueSetFallback(String valueSetUrl, String filter, Throwable t) {
        log.warn("Circuit breaker fallback for expandValueSet: {}", t.getMessage());
        throw new FhirServerUnavailableException("Terminology server unavailable: " + t.getMessage(), t);
    }

    @Cacheable(value = "codeValidation", key = "#system + ':' + #code + ':' + #valueSetUrl")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "validateCodeFallback")
    @Retry(name = "fhirTerminology")
    public boolean validateCode(String system, String code, String valueSetUrl) {
        log.debug("Validating code {} from {} against {}", code, system, valueSetUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(defaultTerminologyServerUrl);

        try {
            Parameters params = new Parameters();
            params.addParameter("url", new UriType(valueSetUrl));
            params.addParameter("system", new UriType(system));
            params.addParameter("code", new CodeType(code));

            Parameters result = client.operation()
                    .onType(ValueSet.class)
                    .named("$validate-code")
                    .withParameters(params)
                    .execute();

            BooleanType resultValue = (BooleanType) result.getParameter("result").getValue();
            return resultValue.booleanValue();
        } catch (Exception e) {
            log.error("Failed to validate code", e);
            throw new FhirServerUnavailableException("Code validation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private boolean validateCodeFallback(String system, String code, String valueSetUrl, Throwable t) {
        log.warn("Circuit breaker fallback for validateCode: {}", t.getMessage());
        throw new FhirServerUnavailableException("Terminology server unavailable: " + t.getMessage(), t);
    }

    @Cacheable(value = "codeLookup", key = "#system + ':' + #code")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "lookupCodeFallback")
    @Retry(name = "fhirTerminology")
    public CodeLookupResult lookupCode(String system, String code) {
        log.debug("Looking up code {} from {}", code, system);

        // Priority 1: Try local TWCORE IG first
        if (igService != null && igService.isLoaded()) {
            CodeLookupResult localResult = lookupCodeFromLocalIg(system, code);
            if (localResult != null) {
                log.info("Code {} from {} found in local TWCORE IG", code, system);
                return localResult;
            }
        }

        // Priority 2: Fallback to remote terminology server
        IGenericClient client = fhirContext.newRestfulGenericClient(defaultTerminologyServerUrl);

        try {
            Parameters params = new Parameters();
            params.addParameter("system", new UriType(system));
            params.addParameter("code", new CodeType(code));

            Parameters result = client.operation()
                    .onType(CodeSystem.class)
                    .named("$lookup")
                    .withParameters(params)
                    .execute();

            String name = null;
            String display = null;
            List<String> designations = new ArrayList<>();

            for (Parameters.ParametersParameterComponent param : result.getParameter()) {
                switch (param.getName()) {
                    case "name":
                        name = ((StringType) param.getValue()).getValue();
                        break;
                    case "display":
                        display = ((StringType) param.getValue()).getValue();
                        break;
                    case "designation":
                        for (Parameters.ParametersParameterComponent part : param.getPart()) {
                            if ("value".equals(part.getName())) {
                                designations.add(((StringType) part.getValue()).getValue());
                            }
                        }
                        break;
                }
            }

            return new CodeLookupResult(system, code, name, display, designations);
        } catch (Exception e) {
            log.error("Failed to lookup code", e);
            throw new FhirServerUnavailableException("Code lookup failed: " + e.getMessage(), e);
        }
    }

    private CodeLookupResult lookupCodeFromLocalIg(String system, String code) {
        try {
            // Search in CodeSystems
            CodeSystem cs = igService.getCodeSystemByUrl(system);
            if (cs != null && cs.hasConcept()) {
                for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                    CodeLookupResult result = findConceptRecursive(concept, system, code, cs.getName());
                    if (result != null) return result;
                }
            }
            // Also search in ValueSets that reference this system
            for (var vsSummary : igService.getValueSets(null)) {
                ValueSet vs = igService.getValueSetByUrl(vsSummary.url());
                if (vs != null && vs.hasCompose()) {
                    for (var include : vs.getCompose().getInclude()) {
                        if (system.equals(include.getSystem()) && include.hasConcept()) {
                            for (var conceptRef : include.getConcept()) {
                                if (code.equals(conceptRef.getCode())) {
                                    return new CodeLookupResult(system, code, vs.getName(),
                                            conceptRef.getDisplay(), new ArrayList<>());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Local IG lookup failed for {} {}: {}", system, code, e.getMessage());
        }
        return null;
    }

    private CodeLookupResult findConceptRecursive(CodeSystem.ConceptDefinitionComponent concept,
                                                    String system, String code, String csName) {
        if (code.equals(concept.getCode())) {
            List<String> designations = new ArrayList<>();
            if (concept.hasDesignation()) {
                for (var d : concept.getDesignation()) {
                    if (d.getValue() != null) designations.add(d.getValue());
                }
            }
            return new CodeLookupResult(system, code, csName, concept.getDisplay(), designations);
        }
        if (concept.hasConcept()) {
            for (var child : concept.getConcept()) {
                CodeLookupResult result = findConceptRecursive(child, system, code, csName);
                if (result != null) return result;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private CodeLookupResult lookupCodeFallback(String system, String code, Throwable t) {
        log.warn("Circuit breaker fallback for lookupCode: {}", t.getMessage());
        // Try local IG as fallback when remote server is unavailable
        if (igService != null && igService.isLoaded()) {
            CodeLookupResult localResult = lookupCodeFromLocalIg(system, code);
            if (localResult != null) return localResult;
        }
        throw new FhirServerUnavailableException("Terminology server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "searchValueSetsFallback")
    @Retry(name = "fhirTerminology")
    public List<ValueSet> searchValueSets(String searchTerm) {
        IGenericClient client = fhirContext.newRestfulGenericClient(defaultTerminologyServerUrl);

        try {
            Bundle bundle = client.search()
                    .forResource(ValueSet.class)
                    .where(ValueSet.TITLE.matches().value(searchTerm))
                    .returnBundle(Bundle.class)
                    .execute();

            List<ValueSet> valueSets = new ArrayList<>();
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource() instanceof ValueSet) {
                    valueSets.add((ValueSet) entry.getResource());
                }
            }
            return valueSets;
        } catch (Exception e) {
            log.error("Failed to search ValueSets", e);
            throw new FhirServerUnavailableException("ValueSet search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private List<ValueSet> searchValueSetsFallback(String searchTerm, Throwable t) {
        log.warn("Circuit breaker fallback for searchValueSets: {}", t.getMessage());
        throw new FhirServerUnavailableException("Terminology server unavailable: " + t.getMessage(), t);
    }

    @Cacheable(value = "codeSearch", key = "#system + ':' + #text + ':' + #maxResults")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "searchCodesFallback")
    @Retry(name = "fhirTerminology")
    public List<CodeSearchResult> searchCodes(String system, String text, int maxResults) {
        log.info("Searching codes in {} for '{}' (max {})", system, text, maxResults);

        List<CodeSearchResult> results = new ArrayList<>();

        // Priority 1: Search local TWCORE IG first
        if (igService != null && igService.isLoaded()) {
            try {
                // Search in CodeSystems
                CodeSystem cs = igService.getCodeSystemByUrl(system);
                if (cs != null && cs.hasConcept()) {
                    String lowerText = text.toLowerCase();
                    searchConceptsRecursive(cs.getConcept(), system, lowerText, results);
                }
                // Also search in ValueSets that reference this system
                for (var vsSummary : igService.getValueSets(null)) {
                    ValueSet vs = igService.getValueSetByUrl(vsSummary.url());
                    if (vs != null && vs.hasCompose()) {
                        for (var include : vs.getCompose().getInclude()) {
                            if (system.equals(include.getSystem()) && include.hasConcept()) {
                                String lowerText = text.toLowerCase();
                                for (var conceptRef : include.getConcept()) {
                                    boolean matches = (conceptRef.getCode() != null && conceptRef.getCode().toLowerCase().contains(lowerText))
                                            || (conceptRef.getDisplay() != null && conceptRef.getDisplay().toLowerCase().contains(lowerText));
                                    if (matches) {
                                        CodeSearchResult localResult = new CodeSearchResult(system, conceptRef.getCode(), conceptRef.getDisplay());
                                        if (!results.contains(localResult)) {
                                            results.add(localResult);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!results.isEmpty()) {
                    log.info("Found {} results from local TWCORE IG for '{}' in {}", results.size(), text, system);
                }
            } catch (Exception e) {
                log.debug("Local IG code search failed: {}", e.getMessage());
            }
        }

        // Priority 2: If local results are insufficient, supplement with remote results
        if (results.size() < maxResults) {
            int needed = maxResults - results.size();
            List<CodeSearchResult> remoteResults = tryExpandOnServer(defaultTerminologyServerUrl, system, text, needed);

            // If primary returned empty, try fallback servers
            if (remoteResults.isEmpty()) {
                for (String fallbackUrl : FALLBACK_TERMINOLOGY_SERVERS) {
                    if (!fallbackUrl.equals(defaultTerminologyServerUrl)) {
                        log.info("Primary server returned no results, trying fallback: {}", fallbackUrl);
                        remoteResults = tryExpandOnServer(fallbackUrl, system, text, needed);
                        if (!remoteResults.isEmpty()) break;
                    }
                }
            }

            // Merge remote results (avoid duplicates)
            for (CodeSearchResult remote : remoteResults) {
                if (!results.contains(remote)) {
                    results.add(remote);
                }
            }
        }

        // Boost: sort codes that appear in any TW Core IG ValueSet to the top
        if (igService != null && igService.isLoaded() && !results.isEmpty()) {
            Set<String> igCodes = collectIgReferencedCodes(system);
            if (!igCodes.isEmpty()) {
                List<CodeSearchResult> boosted = new ArrayList<>();
                List<CodeSearchResult> rest = new ArrayList<>();
                for (CodeSearchResult r : results) {
                    if (igCodes.contains(r.code())) {
                        boosted.add(r);
                    } else {
                        rest.add(r);
                    }
                }
                if (!boosted.isEmpty()) {
                    log.info("Boosted {} TW Core IG codes to top for '{}' in {}", boosted.size(), text, system);
                    results = new ArrayList<>(boosted);
                    results.addAll(rest);
                }
            }
        }

        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    /**
     * Collect all codes referenced by TW Core IG ValueSets for a given code system.
     * This includes codes listed in compose.include[].concept[] (even without display text).
     */
    private Set<String> collectIgReferencedCodes(String system) {
        Set<String> codes = new HashSet<>();
        try {
            for (var vsSummary : igService.getValueSets(null)) {
                ValueSet vs = igService.getValueSetByUrl(vsSummary.url());
                if (vs != null && vs.hasCompose()) {
                    for (var include : vs.getCompose().getInclude()) {
                        if (system.equals(include.getSystem()) && include.hasConcept()) {
                            for (var conceptRef : include.getConcept()) {
                                if (conceptRef.getCode() != null) {
                                    codes.add(conceptRef.getCode());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to collect IG referenced codes for {}: {}", system, e.getMessage());
        }
        return codes;
    }

    private void searchConceptsRecursive(List<CodeSystem.ConceptDefinitionComponent> concepts,
                                          String system, String lowerText, List<CodeSearchResult> results) {
        for (CodeSystem.ConceptDefinitionComponent concept : concepts) {
            boolean matches = (concept.getCode() != null && concept.getCode().toLowerCase().contains(lowerText))
                    || (concept.getDisplay() != null && concept.getDisplay().toLowerCase().contains(lowerText));
            if (matches) {
                CodeSearchResult localResult = new CodeSearchResult(system, concept.getCode(), concept.getDisplay());
                if (!results.contains(localResult)) {
                    results.add(localResult);
                }
            }
            if (concept.hasConcept()) {
                searchConceptsRecursive(concept.getConcept(), system, lowerText, results);
            }
        }
    }

    private List<CodeSearchResult> tryExpandOnServer(String serverUrl, String system, String text, int maxResults) {
        List<CodeSearchResult> results = new ArrayList<>();
        try {
            String implicitVsUrl = IMPLICIT_VALUESET_URLS.getOrDefault(system, system + "?fhir_vs");
            IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);

            // Request extra results to compensate for filtering out non-clinical codes
            int requestCount = maxResults * 3;

            Parameters params = new Parameters();
            params.addParameter("url", new UriType(implicitVsUrl));
            params.addParameter("filter", new StringType(text));
            params.addParameter("count", new IntegerType(requestCount));

            ValueSet expanded = client.operation()
                    .onType(ValueSet.class)
                    .named("$expand")
                    .withParameters(params)
                    .returnResourceType(ValueSet.class)
                    .execute();

            if (expanded.hasExpansion() && expanded.getExpansion().hasContains()) {
                boolean isLoinc = "http://loinc.org".equals(system);
                for (ValueSet.ValueSetExpansionContainsComponent concept : expanded.getExpansion().getContains()) {
                    String code = concept.getCode();
                    // Filter out LOINC Part codes (LP*) - these are hierarchy/grouping codes,
                    // not clinical observation codes used in CQL expressions
                    if (isLoinc && code != null && code.startsWith("LP")) {
                        continue;
                    }
                    results.add(new CodeSearchResult(
                            concept.getSystem() != null ? concept.getSystem() : system,
                            code,
                            concept.getDisplay()
                    ));
                }
            }
            log.info("Server {} returned {} results (after filtering) for '{}' in {}", serverUrl, results.size(), text, system);
        } catch (Exception e) {
            log.warn("Code search failed on server {} for system {}: {}", serverUrl, system, e.getMessage());
        }
        return results;
    }

    @SuppressWarnings("unused")
    private List<CodeSearchResult> searchCodesFallback(String system, String text, int maxResults, Throwable t) {
        log.warn("Circuit breaker fallback for searchCodes: {}", t.getMessage());
        // Try local IG only
        if (igService != null && igService.isLoaded()) {
            List<CodeSearchResult> results = new ArrayList<>();
            CodeSystem cs = igService.getCodeSystemByUrl(system);
            if (cs != null && cs.hasConcept()) {
                String lowerText = text.toLowerCase();
                for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                    boolean matches = (concept.getCode() != null && concept.getCode().toLowerCase().contains(lowerText))
                            || (concept.getDisplay() != null && concept.getDisplay().toLowerCase().contains(lowerText));
                    if (matches) {
                        results.add(new CodeSearchResult(system, concept.getCode(), concept.getDisplay()));
                    }
                    if (results.size() >= maxResults) break;
                }
            }
            return results;
        }
        return new ArrayList<>();
    }

    public void evictCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("Evicted cache: {}", cacheName);
        } else {
            log.warn("Cache not found: {}", cacheName);
        }
    }

    public Map<String, Map<String, Object>> getCacheStats() {
        Map<String, Map<String, Object>> stats = new HashMap<>();
        for (String name : List.of("valueSets", "codeValidation", "codeLookup", "cqlValidation", "vsacValueSets", "codeSearch")) {
            Cache cache = cacheManager.getCache(name);
            if (cache instanceof CaffeineCache caffeineCache) {
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                com.github.benmanes.caffeine.cache.stats.CacheStats cacheStats = nativeCache.stats();
                Map<String, Object> cacheInfo = new HashMap<>();
                cacheInfo.put("size", nativeCache.estimatedSize());
                cacheInfo.put("hitCount", cacheStats.hitCount());
                cacheInfo.put("missCount", cacheStats.missCount());
                cacheInfo.put("hitRate", cacheStats.hitRate());
                stats.put(name, cacheInfo);
            }
        }
        return stats;
    }

    public ValueSet expandValueSetLocal(String url) {
        if (igService != null && igService.isLoaded()) {
            ValueSet vs = igService.getValueSetByUrl(url);
            if (vs != null) {
                log.debug("Expanding ValueSet locally from IG: {}", url);
                return vs;
            }
        }
        return null;
    }

    public List<ValueSet> searchLocalValueSets(String searchTerm) {
        if (igService == null || !igService.isLoaded()) {
            return new ArrayList<>();
        }
        String lowerSearch = searchTerm != null ? searchTerm.toLowerCase() : "";
        return igService.getValueSets(searchTerm).stream()
                .map(summary -> igService.getValueSetByUrl(summary.url()))
                .filter(vs -> vs != null)
                .toList();
    }

    public record CodeLookupResult(
            String system,
            String code,
            String name,
            String display,
            List<String> designations
    ) {}

    public record CodeSearchResult(
            String system,
            String code,
            String display
    ) {}
}
