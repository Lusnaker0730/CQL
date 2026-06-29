package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FhirTerminologyService {

    private static final List<String> FALLBACK_TERMINOLOGY_SERVERS = List.of(
            "https://tx.fhir.org/r4",
            "https://r4.ontoserver.csiro.au/fhir"
    );

    private static final String RXNORM_SYSTEM = "http://www.nlm.nih.gov/research/umls/rxnorm";
    private static final String RXNAV_APPROX_URL = "https://rxnav.nlm.nih.gov/REST/approximateTerm.json";

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
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private final ConcurrentHashMap<String, TerminologyProvider> terminologyProviderCache =
            new ConcurrentHashMap<>();

    public TerminologyProvider createTerminologyProvider(String terminologyServerUrl) {
        String serverUrl = terminologyServerUrl != null ? terminologyServerUrl : defaultTerminologyServerUrl;
        return terminologyProviderCache.computeIfAbsent(serverUrl, url -> {
            IGenericClient client = fhirContext.newRestfulGenericClient(url);
            TerminologyProvider remoteProvider = new R4FhirTerminologyProvider(client);

            boolean hasIg = igService != null && igService.isLoaded();
            boolean hasVsac = vsacService != null;

            if (hasIg || hasVsac) {
                log.debug("Creating terminology provider with local IG={}, VSAC={}", hasIg, hasVsac);
                return new LocalTerminologyProvider(hasIg ? igService : null, remoteProvider, hasVsac ? vsacService : null);
            }
            return remoteProvider;
        });
    }

    @Cacheable(value = "valueSets", key = "#valueSetUrl")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "expandValueSetFallback")
    @Retry(name = "fhirTerminology")
    public ValueSet expandValueSet(String valueSetUrl, String filter) {
        log.debug("Expanding ValueSet: {}", valueSetUrl);

        // PAT-166: Priority 1 — try local IG before going remote (aligns with
        // lookupCode's pattern). tx.fhir.org returns 422 for canonical URLs it
        // doesn't know (e.g. TW Core), so a bundled IG was being ignored for
        // expansion even though its definitions were already on the classpath.
        if (igService != null && igService.isLoaded()) {
            ValueSet local = igService.getValueSetByUrl(valueSetUrl);
            if (local != null) {
                ValueSet expanded = tryLocalExpand(local, filter);
                if (expanded != null) {
                    log.info("Expanded {} from local IG ({} concepts)",
                            valueSetUrl, expanded.getExpansion().getContains().size());
                    return expanded;
                }
                // Compose contains filter / VS reference / unresolved external
                // CodeSystem — we can't enumerate codes locally, but at least
                // return the VS definition so the UI sees compose rules instead
                // of a 503.
                log.info("Returning local definition for {} (compose needs terminology server to enumerate)", valueSetUrl);
                return local;
            }
        }

        // Priority 2 — remote terminology server (existing behavior)
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

    /**
     * PAT-166: Try to enumerate a ValueSet using only locally-bundled IG
     * resources. Returns {@code null} (caller falls back to definition body)
     * if any compose include uses filters (e.g. {@code is-a 404684003}),
     * references another ValueSet, or pulls from a CodeSystem we don't have
     * locally — those need a SCT-aware terminology server to enumerate.
     */
    ValueSet tryLocalExpand(ValueSet source, String filter) {
        if (!source.hasCompose()) return null;

        String normalizedFilter = filter == null ? null : filter.toLowerCase();
        ValueSet expanded = source.copy();
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.setTimestamp(new java.util.Date());
        expansion.setIdentifier("urn:uuid:" + java.util.UUID.randomUUID());

        for (ValueSet.ConceptSetComponent include : source.getCompose().getInclude()) {
            // Filter-based (e.g. SNOMED is-a hierarchy) — only a SCT-aware
            // terminology server can enumerate these.
            if (include.hasFilter() && !include.getFilter().isEmpty()) return null;
            // Chained VS reference — would need recursive resolution; skip.
            if (include.hasValueSet() && !include.getValueSet().isEmpty()) return null;

            String system = include.getSystem();
            if (system == null) return null;

            if (include.hasConcept()) {
                // Case A: explicit concept enumeration in compose
                for (ValueSet.ConceptReferenceComponent c : include.getConcept()) {
                    if (matchesFilter(c.getCode(), c.getDisplay(), normalizedFilter)) {
                        expansion.addContains()
                                .setSystem(system)
                                .setCode(c.getCode())
                                .setDisplay(c.getDisplay());
                    }
                }
            } else {
                // Case B: include *all* codes from a CodeSystem; only works
                // when the CS is in our local IG bundle.
                CodeSystem cs = igService.getCodeSystemByUrl(system);
                if (cs == null || !cs.hasConcept()) return null;
                for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                    addConceptsRecursive(concept, system, expansion, normalizedFilter);
                }
            }
        }

        expansion.setTotal(expansion.getContains().size());
        expanded.setExpansion(expansion);
        return expanded;
    }

    private void addConceptsRecursive(CodeSystem.ConceptDefinitionComponent concept,
                                       String system,
                                       ValueSet.ValueSetExpansionComponent expansion,
                                       String filter) {
        if (matchesFilter(concept.getCode(), concept.getDisplay(), filter)) {
            expansion.addContains()
                    .setSystem(system)
                    .setCode(concept.getCode())
                    .setDisplay(concept.getDisplay());
        }
        if (concept.hasConcept()) {
            for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
                addConceptsRecursive(child, system, expansion, filter);
            }
        }
    }

    private boolean matchesFilter(String code, String display, String filter) {
        if (filter == null || filter.isBlank()) return true;
        if (code != null && code.toLowerCase().contains(filter)) return true;
        if (display != null && display.toLowerCase().contains(filter)) return true;
        return false;
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

            return CodeLookupResult.found(system, code, name, display, designations);
        } catch (BaseServerResponseException re) {
            // BUG-124: a 4xx from the terminology server means it understood the
            // request and is telling us the code/system is not recognised (or the
            // input is invalid) — a normal NEGATIVE result, NOT a server outage.
            // Return a cacheable not-found (so the same unknown code is not looked
            // up remotely again for the cache TTL) instead of throwing a 503.
            // Only genuine upstream failures (5xx, or status 0 = connection/timeout
            // with no HTTP response) become FhirServerUnavailableException.
            if (isClientError(re)) {
                log.debug("Code {} from {} not found on {} (HTTP {})",
                        code, system, defaultTerminologyServerUrl, re.getStatusCode());
                return CodeLookupResult.notFound(system, code);
            }
            log.error("Terminology server error during $lookup (HTTP {})", re.getStatusCode(), re);
            throw new FhirServerUnavailableException("Code lookup failed: " + re.getMessage(), re);
        } catch (Exception e) {
            // Connection refused / timeout / unknown cause — upstream genuinely
            // unavailable (Reason auto-classified from the cause chain).
            log.error("Failed to lookup code", e);
            throw new FhirServerUnavailableException("Code lookup failed: " + e.getMessage(), e);
        }
    }

    /**
     * BUG-124: classify a HAPI client exception thrown by a terminology
     * operation. A 4xx status means the server understood the request and
     * reported the code/system as not found or the input as invalid — a
     * negative result that must NOT be treated as a server outage. A 5xx, or
     * status {@code 0} (which HAPI uses for connection/timeout failures with no
     * HTTP response), is a genuine upstream failure. Package-private + static so
     * it can be unit-tested without a live terminology server.
     */
    static boolean isClientError(BaseServerResponseException ex) {
        int status = ex.getStatusCode();
        return status >= 400 && status < 500;
    }

    private CodeLookupResult lookupCodeFromLocalIg(String system, String code) {
        try {
            // Search in CodeSystems (authoritative — have display names)
            CodeSystem cs = igService.getCodeSystemByUrl(system);
            if (cs != null && cs.hasConcept()) {
                for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                    CodeLookupResult result = findConceptRecursive(concept, system, code, cs.getName());
                    if (result != null) return result;
                }
            }
            // Also search in ValueSets that reference this system.
            // Only return if the ValueSet entry has a non-empty display;
            // otherwise fall through to the remote server for richer metadata.
            for (var vsSummary : igService.getValueSets(null)) {
                ValueSet vs = igService.getValueSetByUrl(vsSummary.url());
                if (vs != null && vs.hasCompose()) {
                    for (var include : vs.getCompose().getInclude()) {
                        if (system.equals(include.getSystem()) && include.hasConcept()) {
                            for (var conceptRef : include.getConcept()) {
                                if (code.equals(conceptRef.getCode())) {
                                    String display = conceptRef.getDisplay();
                                    if (display != null && !display.isBlank()) {
                                        return CodeLookupResult.found(system, code, null,
                                                display, new ArrayList<>());
                                    }
                                    // display is empty — skip, let remote server provide it
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // PAT-142: distinguish "IG not loaded" (expected — fall through to remote)
            // from "IG loaded but lookup threw" (unexpected — IG resource may be
            // corrupt; surface so operators can investigate). Previously both went
            // to DEBUG and looked identical to a "code not in local IG" miss.
            if (igService != null && igService.isLoaded()) {
                log.warn("Local IG lookup unexpectedly threw for {} {} (IG is loaded — possible corrupt resource): {}",
                        system, code, e.getMessage());
            } else {
                log.debug("Local IG lookup skipped for {} {} (IG not loaded): {}", system, code, e.getMessage());
            }
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
            return CodeLookupResult.found(system, code, csName, concept.getDisplay(), designations);
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
                // PAT-142: same loaded-vs-not distinction as lookupCodeFromLocalIg.
                if (igService.isLoaded()) {
                    log.warn("Local IG code search unexpectedly threw (IG is loaded — possible corrupt resource): {}",
                            e.getMessage());
                } else {
                    log.debug("Local IG code search skipped (IG not loaded): {}", e.getMessage());
                }
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

            // Fallback: For RxNorm, use NLM RxNav API when FHIR servers have no data
            if (remoteResults.isEmpty() && RXNORM_SYSTEM.equals(system)) {
                log.info("FHIR servers returned no RxNorm results, falling back to RxNav API");
                List<CodeSearchResult> rxNavResults = tryRxNavSearch(text, needed);
                for (CodeSearchResult r : rxNavResults) {
                    if (!results.contains(r)) {
                        results.add(r);
                    }
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

    /**
     * Fallback search using NLM RxNav REST API for RxNorm codes.
     * RxNav is a free, public API that does not require authentication.
     */
    private List<CodeSearchResult> tryRxNavSearch(String text, int maxResults) {
        List<CodeSearchResult> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = RXNAV_APPROX_URL + "?term=" + encoded + "&maxEntries=" + maxResults;
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode groups = root.path("approximateGroup").path("candidate");

            // Deduplicate by rxcui — the API returns multiple entries per concept from different sources
            Map<String, String> seen = new LinkedHashMap<>();
            if (groups.isArray()) {
                for (JsonNode candidate : groups) {
                    String rxcui = candidate.path("rxcui").asText("");
                    String name = candidate.path("name").asText("");
                    if (!rxcui.isEmpty() && !name.isEmpty() && !seen.containsKey(rxcui)) {
                        seen.put(rxcui, name);
                    }
                }
            }

            for (var entry : seen.entrySet()) {
                results.add(new CodeSearchResult(RXNORM_SYSTEM, entry.getKey(), entry.getValue()));
                if (results.size() >= maxResults) break;
            }
            log.info("RxNav API returned {} unique results for '{}'", results.size(), text);
        } catch (Exception e) {
            log.warn("RxNav API search failed for '{}': {}", text, e.getMessage());
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
        for (String name : List.of("valueSets", "codeValidation", "codeLookup", "cqlValidation", "cqlTranslation", "vsacValueSets", "codeSearch")) {
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

    /**
     * Result of a CodeSystem {@code $lookup}. {@code found=false} means no
     * source (local IG or terminology server) recognises the code — a normal
     * negative result, NOT a server error. The service RETURNS this (rather
     * than throwing) so the {@code codeLookup} cache stores it, preventing
     * repeat remote calls for the same unknown code (BUG-124). The controller
     * maps {@code found=false} to HTTP 404, never a 5xx.
     */
    public record CodeLookupResult(
            String system,
            String code,
            String name,
            String display,
            List<String> designations,
            boolean found
    ) {
        /** A successful lookup — the code exists in some source. */
        public static CodeLookupResult found(String system, String code, String name,
                                             String display, List<String> designations) {
            return new CodeLookupResult(system, code, name, display, designations, true);
        }

        /** A negative result — the code is not recognised by any source. */
        public static CodeLookupResult notFound(String system, String code) {
            return new CodeLookupResult(system, code, null, null, List.of(), false);
        }
    }

    public record CodeSearchResult(
            String system,
            String code,
            String display
    ) {}
}
