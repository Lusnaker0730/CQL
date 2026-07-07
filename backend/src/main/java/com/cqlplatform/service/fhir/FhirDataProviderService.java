package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import com.cqlplatform.exception.FhirServerUnavailableException;
import com.cqlplatform.fhir.FhirErrorInspector;
import com.cqlplatform.service.cql.CircuitBreakerRetrieveProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import com.cqlplatform.service.cql.ComparableR4FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.retrieve.RestFhirRetrieveProvider;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirDataProviderService {

    private final FhirContext fhirContext;
    private final FhirClientFactory fhirClientFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${fhir.server.url:http://hapi-fhir:8080/fhir}")
    private String defaultFhirServerUrl;

    @Value("${fhir.patient.batch-size:100}")
    private int patientBatchSize;

    @Value("${cql.execution.max-patient-count:50000}")
    private int maxPatientCount;

    private final AtomicInteger retrieveCount = new AtomicInteger(0);

    public IGenericClient createClient(String fhirServerUrl) {
        return fhirClientFactory.createClient(fhirServerUrl);
    }

    /**
     * Batch-fetch all resources of the given types for a patient in a single FHIR Batch request.
     * Returns the combined list of resources for use with PrefetchRetrieveProvider.
     */
    public List<Resource> batchFetchPatientResources(String fhirServerUrl, String patientId, Set<String> resourceTypes) {
        Bundle batch = new Bundle();
        batch.setType(Bundle.BundleType.BATCH);

        // Direct read for Patient
        batch.addEntry()
                .getRequest()
                .setMethod(Bundle.HTTPVerb.GET)
                .setUrl("Patient/" + patientId);

        // Search for each clinical resource type (request up to 500 per type to avoid pagination truncation)
        for (String resourceType : resourceTypes) {
            if ("Patient".equals(resourceType)) continue;
            String searchParam = PATIENT_BASED_RESOURCES.contains(resourceType) ? "patient" : "subject";
            batch.addEntry()
                    .getRequest()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl(resourceType + "?" + searchParam + "=Patient/" + patientId + "&_count=500");
        }

        log.debug("Batch prefetch: {} entries for patient {}", batch.getEntry().size(), patientId);
        Bundle response = executeTransaction(fhirServerUrl, batch);

        List<Resource> resources = new ArrayList<>();
        if (response.hasEntry()) {
            for (Bundle.BundleEntryComponent entry : response.getEntry()) {
                Resource resource = entry.getResource();
                if (resource instanceof Bundle resultBundle) {
                    if (resultBundle.hasEntry()) {
                        for (Bundle.BundleEntryComponent resultEntry : resultBundle.getEntry()) {
                            if (resultEntry.getResource() != null) {
                                resources.add(resultEntry.getResource());
                            }
                        }
                    }
                } else if (resource != null) {
                    resources.add(resource);
                }
            }
        }

        log.info("Batch prefetch result: {} resources for {} types", resources.size(), resourceTypes.size());
        return resources;
    }

    /**
     * Bulk-fetch resources for ALL patients in a single FHIR batch request per resource type.
     * Returns a map of patientId → list of resources, ready for PrefetchRetrieveProvider.
     * This is much faster than per-patient batch fetch when evaluating many patients.
     */
    @CircuitBreaker(name = "fhirServer")
    @Retry(name = "fhirServer")
    public java.util.Map<String, List<Resource>> bulkFetchAllPatients(
            String fhirServerUrl, List<String> patientIds, Set<String> resourceTypes) {
        IGenericClient client = createClient(fhirServerUrl);
        java.util.Map<String, List<Resource>> result = new java.util.concurrent.ConcurrentHashMap<>();
        for (String pid : patientIds) {
            result.put(pid, new ArrayList<>());
        }

        long start = System.currentTimeMillis();

        // PAT-112a: do NOT swallow per-resourceType or per-patient-batch failures.
        // A failed bulk fetch previously left the result sparse with no upstream
        // signal, so measure evaluation ran against incomplete data and the score
        // silently skewed. Fail loud instead — the caller (measure evaluation /
        // CDS invocation) propagates the exception to GlobalExceptionHandler which
        // turns it into a FHIR_UPSTREAM_UNAVAILABLE envelope (PAT-110).
        for (String resourceType : resourceTypes) {
            // Patient resources are fetched separately via batch GET below (L161+).
            // Querying `Patient?subject=...` or `Patient?patient=...` is invalid (HAPI rejects
            // with HAPI-0524 — neither is a Patient search param). Callers may legitimately
            // include Patient in the retrieveTypes set; skipping here keeps the loop safe.
            if ("Patient".equals(resourceType)) {
                continue;
            }
            try {
                String searchParam = PATIENT_BASED_RESOURCES.contains(resourceType) ? "patient" : "subject";
                String patientList = String.join(",", patientIds.stream()
                        .map(id -> "Patient/" + id).toList());

                Bundle searchResult = client.search()
                        .byUrl(resourceType + "?" + searchParam + "=" + patientList + "&_count=5000")
                        .returnBundle(Bundle.class)
                        .execute();

                // PAT-112c: 200 OK with embedded OperationOutcome(severity=error|fatal)
                // must not be treated as valid data.
                FhirErrorInspector.assertNoErrors(searchResult, "bulk fetch " + resourceType);

                // Distribute resources to their patient buckets
                if (searchResult.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : searchResult.getEntry()) {
                        Resource resource = entry.getResource();
                        if (resource == null) continue;
                        String ownerPatientId = extractPatientId(resource);
                        if (ownerPatientId != null && result.containsKey(ownerPatientId)) {
                            result.get(ownerPatientId).add(resource);
                        }
                    }
                }
            } catch (FhirServerUnavailableException e) {
                throw e;
            } catch (Exception e) {
                log.error("Bulk fetch failed for {}: {}", resourceType, e.getMessage());
                throw new FhirServerUnavailableException(
                        "Bulk fetch failed for " + resourceType + ": " + e.getMessage(), e);
            }
        }

        // Also fetch Patient resources — same fail-loud rule applies.
        Bundle patientBatch = new Bundle();
        patientBatch.setType(Bundle.BundleType.BATCH);
        for (String pid : patientIds) {
            patientBatch.addEntry().getRequest()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl("Patient/" + pid);
        }
        try {
            Bundle patientResponse = executeTransaction(fhirServerUrl, patientBatch);
            // PAT-112c: batch transaction responses carry per-entry outcomes; reject any
            // fatal/error before distributing resources.
            FhirErrorInspector.assertNoErrors(patientResponse, "bulk Patient batch");
            if (patientResponse.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : patientResponse.getEntry()) {
                    if (entry.getResource() instanceof Patient patient && patient.hasId()) {
                        String pid = patient.getIdElement().getIdPart();
                        if (result.containsKey(pid)) {
                            result.get(pid).add(patient);
                        }
                    }
                }
            }
        } catch (FhirServerUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Bulk Patient fetch failed: {}", e.getMessage());
            throw new FhirServerUnavailableException(
                    "Bulk Patient fetch failed: " + e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        int totalResources = result.values().stream().mapToInt(List::size).sum();
        log.info("Bulk fetch complete: {} resources for {} patients in {}ms ({} resource types)",
                totalResources, patientIds.size(), elapsed, resourceTypes.size());

        return result;
    }

    /**
     * Extract the patient ID from a FHIR resource's subject/patient reference.
     */
    private String extractPatientId(Resource resource) {
        try {
            if (resource instanceof org.hl7.fhir.r4.model.DomainResource dr) {
                // Use reflection-free approach for common types
                if (dr instanceof Observation obs && obs.hasSubject()) {
                    return obs.getSubject().getReferenceElement().getIdPart();
                } else if (dr instanceof Condition cond && cond.hasSubject()) {
                    return cond.getSubject().getReferenceElement().getIdPart();
                } else if (dr instanceof Encounter enc && enc.hasSubject()) {
                    return enc.getSubject().getReferenceElement().getIdPart();
                } else if (dr instanceof MedicationRequest mr && mr.hasSubject()) {
                    return mr.getSubject().getReferenceElement().getIdPart();
                } else if (dr instanceof AllergyIntolerance ai && ai.hasPatient()) {
                    return ai.getPatient().getReferenceElement().getIdPart();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public RetrieveProvider createDataProvider(String fhirServerUrl, TerminologyProvider terminologyProvider) {
        return createDataProvider(fhirServerUrl, terminologyProvider, null);
    }

    /**
     * Build a RetrieveProvider. When {@code connection} is non-null the FHIR client is
     * built with that connection's credentials (Basic / Bearer / SMART Backend / mTLS)
     * so CQL executes against a clinic's own secured FHIR server; otherwise an
     * unauthenticated client for {@code fhirServerUrl} is used (unchanged behaviour).
     */
    public RetrieveProvider createDataProvider(String fhirServerUrl, TerminologyProvider terminologyProvider,
                                               com.cqlplatform.entity.EhrConnectionEntity connection) {
        log.debug("Creating RetrieveProvider for URL: {} (authenticated={})", fhirServerUrl, connection != null);
        IGenericClient client = connection != null
                ? fhirClientFactory.createAuthenticatedClient(connection)
                : createClient(fhirServerUrl);
        SearchParameterResolver searchParameterResolver = new SearchParameterResolver(fhirContext);

        try {
            log.debug("Resolver check - Observation.patient path: {}",
                    searchParameterResolver.getSearchParameterDefinition("Observation", "patient").getPath());
        } catch (Exception e) {
            log.debug("Resolver check failed: {}", e.getMessage());
        }

        RestFhirRetrieveProvider retrieveProvider = new RestFhirRetrieveProvider(
                searchParameterResolver,
                new ComparableR4FhirModelResolver(),
                client);

        retrieveProvider.setTerminologyProvider(terminologyProvider);

        // Wrap with circuit breaker — shares "fhirDataProvider" CB with service-level methods
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker("fhirDataProvider");
        RetrieveProvider cbProvider = new CircuitBreakerRetrieveProvider(retrieveProvider, cb);

        return new CountingRetrieveProvider(cbProvider, retrieveCount, fhirClientFactory, processUrl(fhirServerUrl));
    }

    private String processUrl(String url) {
        return url != null ? url : defaultFhirServerUrl;
    }

    private static final int MAX_SEARCH_RESULTS = 10000;

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "searchResourcesFallback")
    @Retry(name = "fhirDataProvider")
    public Bundle searchResources(String fhirServerUrl, String resourceType, String searchParams) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            Bundle bundle = client.search()
                    .forResource(resourceType)
                    .whereMap(parseSearchParams(searchParams))
                    .count(200)
                    .returnBundle(Bundle.class)
                    .execute();

            // Collect all pages into one Bundle
            List<Bundle.BundleEntryComponent> allEntries = new ArrayList<>(
                    bundle.getEntry() != null ? bundle.getEntry() : List.of());

            while (bundle.getLink(Bundle.LINK_NEXT) != null && allEntries.size() < MAX_SEARCH_RESULTS) {
                bundle = client.loadPage().next(bundle).execute();
                if (bundle.getEntry() != null) {
                    allEntries.addAll(bundle.getEntry());
                }
            }

            // Build a single result Bundle with all entries
            Bundle result = new Bundle();
            result.setType(Bundle.BundleType.SEARCHSET);
            result.setTotal(allEntries.size());
            result.setEntry(allEntries);
            if (allEntries.size() >= MAX_SEARCH_RESULTS) {
                log.warn("Search results truncated at {} for {}", MAX_SEARCH_RESULTS, resourceType);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to search FHIR resources", e);
            throw new FhirServerUnavailableException("FHIR search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Bundle searchResourcesFallback(String fhirServerUrl, String resourceType, String searchParams, Throwable t) {
        log.warn("Circuit breaker fallback for searchResources: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "getResourceFallback")
    @Retry(name = "fhirDataProvider")
    public Resource getResource(String fhirServerUrl, String resourceType, String id) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.read()
                    .resource(resourceType)
                    .withId(id)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to read FHIR resource", e);
            throw new FhirServerUnavailableException("FHIR read failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Resource getResourceFallback(String fhirServerUrl, String resourceType, String id, Throwable t) {
        log.warn("Circuit breaker fallback for getResource: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "createResourceFallback")
    @Retry(name = "fhirDataProvider")
    public Resource createResource(String fhirServerUrl, Resource resource) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.create()
                    .resource(resource)
                    .execute()
                    .getResource();
        } catch (Exception e) {
            log.error("Failed to create FHIR resource", e);
            throw new FhirServerUnavailableException("FHIR create failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Resource createResourceFallback(String fhirServerUrl, Resource resource, Throwable t) {
        log.warn("Circuit breaker fallback for createResource: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "updateResourceFallback")
    @Retry(name = "fhirDataProvider")
    public Resource updateResource(String fhirServerUrl, Resource resource) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.update()
                    .resource(resource)
                    .execute()
                    .getResource();
        } catch (Exception e) {
            log.error("Failed to update FHIR resource", e);
            throw new FhirServerUnavailableException("FHIR update failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Resource updateResourceFallback(String fhirServerUrl, Resource resource, Throwable t) {
        log.warn("Circuit breaker fallback for updateResource: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "deleteResourceFallback")
    @Retry(name = "fhirDataProvider")
    public void deleteResource(String fhirServerUrl, String resourceType, String id) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            client.delete()
                    .resourceById(resourceType, id)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to delete FHIR resource", e);
            throw new FhirServerUnavailableException("FHIR delete failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private void deleteResourceFallback(String fhirServerUrl, String resourceType, String id, Throwable t) {
        log.warn("Circuit breaker fallback for deleteResource: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "getAllPatientIdsFallback")
    @Retry(name = "fhirDataProvider")
    public List<String> getAllPatientIds(String fhirServerUrl) {
        IGenericClient client = createClient(fhirServerUrl);
        List<String> patientIds = new ArrayList<>();

        try {
            Bundle bundle = client.search()
                    .forResource("Patient")
                    .elementsSubset("id")
                    .count(patientBatchSize)
                    .returnBundle(Bundle.class)
                    .execute();

            while (bundle != null) {
                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.getResource() instanceof Patient) {
                            patientIds.add(entry.getResource().getIdElement().getIdPart());
                        }
                    }
                }

                if (patientIds.size() >= maxPatientCount) {
                    log.warn("Patient ID fetch reached max count ({}). Stopping pagination.", maxPatientCount);
                    bundle = null;
                } else if (bundle.getLink(Bundle.LINK_NEXT) != null) {
                    bundle = client.loadPage().next(bundle).execute();
                } else {
                    bundle = null;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch all patients", e);
            throw new FhirServerUnavailableException("Failed to fetch patient list: " + e.getMessage(), e);
        }

        log.debug("Fetched {} patient IDs (batch size: {})", patientIds.size(), patientBatchSize);
        return patientIds;
    }

    @SuppressWarnings("unused")
    private List<String> getAllPatientIdsFallback(String fhirServerUrl, Throwable t) {
        log.warn("Circuit breaker fallback for getAllPatientIds: {}", t.getMessage());
        throw new FhirServerUnavailableException(
                "Unable to fetch patient list from FHIR server: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "executeTransactionFallback")
    @Retry(name = "fhirDataProvider")
    public Bundle executeTransaction(String fhirServerUrl, Bundle bundle) {
        if (bundle.getType() != Bundle.BundleType.BATCH && bundle.getType() != Bundle.BundleType.TRANSACTION) {
            throw new IllegalArgumentException("Bundle type must be BATCH or TRANSACTION");
        }
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return client.transaction().withBundle(bundle).execute();
        } catch (Exception e) {
            log.error("Failed to execute FHIR transaction", e);
            throw new FhirServerUnavailableException("FHIR transaction failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Bundle executeTransactionFallback(String fhirServerUrl, Bundle bundle, Throwable t) {
        log.warn("Circuit breaker fallback for executeTransaction: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "searchPatientsByDemographicsFallback")
    @Retry(name = "fhirDataProvider")
    public Bundle searchPatientsByDemographics(String fhirServerUrl,
                                                String family, String given, String birthdate, String identifier) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            var search = client.search().forResource(Patient.class);

            if (family != null && !family.isBlank()) {
                search = search.where(Patient.FAMILY.matches().value(family));
            }
            if (given != null && !given.isBlank()) {
                search = search.where(Patient.GIVEN.matches().value(given));
            }
            if (birthdate != null && !birthdate.isBlank()) {
                search = search.where(Patient.BIRTHDATE.exactly().day(birthdate));
            }
            if (identifier != null && !identifier.isBlank()) {
                search = search.where(Patient.IDENTIFIER.exactly().code(identifier));
            }

            return search.returnBundle(Bundle.class).execute();
        } catch (Exception e) {
            log.error("Failed to search patients by demographics", e);
            throw new FhirServerUnavailableException("Patient demographics search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Bundle searchPatientsByDemographicsFallback(String fhirServerUrl,
                                                         String family, String given, String birthdate, String identifier, Throwable t) {
        log.warn("Circuit breaker fallback for searchPatientsByDemographics: {}", t.getMessage());
        throw new FhirServerUnavailableException("FHIR server unavailable: " + t.getMessage(), t);
    }

    public int getAndResetRetrieveCount() {
        return retrieveCount.getAndSet(0);
    }

    private java.util.Map<String, java.util.List<String>> parseSearchParams(String searchParams) {
        java.util.Map<String, java.util.List<String>> params = new java.util.HashMap<>();
        if (searchParams == null || searchParams.isBlank()) {
            return params;
        }

        for (String param : searchParams.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2) {
                params.computeIfAbsent(parts[0], k -> new java.util.ArrayList<>()).add(parts[1]);
            }
        }
        return params;
    }

    /**
     * Resource types that support 'subject' search parameter for patient-context retrieval.
     * Most clinical resources in FHIR R4 use 'subject' to reference the patient.
     */
    private static final java.util.Set<String> SUBJECT_BASED_RESOURCES = java.util.Set.of(
            "Condition", "Encounter", "MedicationRequest", "MedicationAdministration",
            "Procedure", "DiagnosticReport", "CarePlan", "ServiceRequest",
            "ClinicalImpression", "RiskAssessment", "Goal", "NutritionOrder",
            "DeviceRequest", "Communication", "CommunicationRequest", "DocumentReference",
            "Composition", "Coverage", "Claim", "ExplanationOfBenefit"
    );

    /**
     * Resource types that use 'patient' search parameter instead of 'subject'.
     */
    private static final java.util.Set<String> PATIENT_BASED_RESOURCES = java.util.Set.of(
            "Observation", "AllergyIntolerance", "Immunization", "MedicationStatement",
            "MedicationDispense", "FamilyMemberHistory", "Flag", "Consent",
            "AdverseEvent", "QuestionnaireResponse", "ImagingStudy", "Media",
            "Specimen", "BodyStructure", "DetectedIssue", "SupplyDelivery",
            "SupplyRequest", "VisionPrescription", "Account",
            // Coverage is a financial resource that uses `patient` (not `subject`) per FHIR R4.
            // Surfaced by the smoke harness when SDE Payer's `[Coverage: "..."]` retrieve hit
            // bulkFetchAllPatients with the wrong search param. HAPI-0524 rejected the request.
            "Coverage"
    );

    private static class CountingRetrieveProvider implements RetrieveProvider {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CountingRetrieveProvider.class);

        private final RetrieveProvider delegate;
        private final AtomicInteger counter;
        private final FhirClientFactory clientFactory;
        private final String fhirServerUrl;
        /** Bounded fallback cache — evicts oldest entries when full to prevent unbounded memory growth. */
        private static final int MAX_FALLBACK_CACHE_SIZE = 200;
        private final java.util.concurrent.ConcurrentHashMap<String, List<Object>> fallbackCache =
                new java.util.concurrent.ConcurrentHashMap<>();

        public CountingRetrieveProvider(RetrieveProvider delegate, AtomicInteger counter, FhirClientFactory clientFactory,
                String fhirServerUrl) {
            this.delegate = delegate;
            this.counter = counter;
            this.clientFactory = clientFactory;
            this.fhirServerUrl = fhirServerUrl;
        }

        @Override
        public Iterable<Object> retrieve(String context, String contextPath, Object contextValue,
                String dataType, String templateId, String codePath,
                Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes,
                String valueSet, String datePath, String dateLowPath,
                String dateHighPath, org.opencds.cqf.cql.engine.runtime.Interval dateRange) {
            log.debug("Retrieve: DataType={}, Context={}, ContextPath={}, ContextValue={}, " +
                            "codePath={}, valueSet={}, datePath={}, dateRange={}",
                    dataType, context, contextPath, contextValue,
                    codePath, valueSet, datePath, dateRange);

            // PAT-112a: let exceptions from the delegate propagate. Previously this
            // catch set results=null and downstream returned an empty list, which is
            // indistinguishable from "patient has no data". CDS would render "no
            // alert" on a FHIR outage — a false negative with real patient-safety risk.
            // CQL engine treats a thrown exception as evaluation failure; measure eval
            // + CDS invocation surface it as a FHIR_UPSTREAM_UNAVAILABLE envelope.
            Iterable<Object> results = delegate.retrieve(context, contextPath, contextValue, dataType, templateId,
                    codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);

            List<Object> resultList = new ArrayList<>();
            if (results != null) {
                results.forEach(resultList::add);
            }

            // MANUAL FALLBACK: if delegate returned 0 results (legitimate empty),
            // try a direct FHIR client read. Exceptions from this fallback must
            // also propagate — silently returning an empty list would mask the
            // outage the same way.
            if (resultList.isEmpty() && contextValue != null && "Patient".equals(context)) {
                String patientId = contextValue.toString();
                if (patientId.startsWith("Patient/")) {
                    patientId = patientId.substring("Patient/".length());
                }

                if ("Patient".equals(dataType)) {
                    try {
                        IGenericClient fallbackClient = clientFactory.createClient(fhirServerUrl);
                        Patient patient = fallbackClient.read().resource(Patient.class).withId(patientId).execute();
                        if (patient != null) {
                            resultList.add(patient);
                        }
                    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
                        // Legitimate 404 — patient does not exist. Not an outage.
                        log.debug("Patient/{} not found on upstream (legitimate empty)", patientId);
                    } catch (Exception e) {
                        log.error("Patient/{} fallback read failed (upstream outage): {}", patientId, e.getMessage());
                        throw new FhirServerUnavailableException(
                                "Fallback read Patient/" + patientId + " failed: " + e.getMessage(), e);
                    }
                } else {
                    // Generic fallback: search by subject or patient parameter, with code filtering.
                    // fallbackSearch now also throws on outage rather than returning empty.
                    resultList.addAll(fallbackSearch(dataType, patientId, codePath, codes));
                }
            }

            int currentCount = resultList.size();
            log.debug("Final result count for {}: {}", dataType, currentCount);

            counter.addAndGet(currentCount);
            return resultList;
        }

        /**
         * Generic fallback search: tries 'subject', then 'patient' search parameter.
         * Includes code-based filtering when codePath and codes are provided.
         */
        private List<Object> fallbackSearch(String dataType, String patientId,
                String codePath, Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes) {
            // Collect codes for reuse
            List<org.opencds.cqf.cql.engine.runtime.Code> codeList = collectCodes(codes);

            // Build cache key from search parameters
            StringBuilder ckb = new StringBuilder();
            if (codeList != null) {
                for (org.opencds.cqf.cql.engine.runtime.Code c : codeList) {
                    if (ckb.length() > 0) ckb.append(",");
                    if (c.getSystem() != null) ckb.append(c.getSystem()).append("|");
                    ckb.append(c.getCode());
                }
            }
            String cacheKey = dataType + "|" + patientId + "|" + codePath + "|" + ckb;
            List<Object> cached = fallbackCache.get(cacheKey);
            if (cached != null) {
                log.debug("Fallback cache hit for {}", cacheKey);
                counter.addAndGet(cached.size());
                return cached;
            }

            log.debug("Fallback search for {}?subject/patient={}, codePath={}, hasCodes={}",
                    dataType, patientId, codePath, codeList != null);
            IGenericClient fallbackClient = clientFactory.createClient(fhirServerUrl);

            // Determine which search parameter to try first
            String primaryParam = SUBJECT_BASED_RESOURCES.contains(dataType) ? "subject" : "patient";
            String secondaryParam = "subject".equals(primaryParam) ? "patient" : "subject";

            List<Object> results = trySearch(fallbackClient, dataType, primaryParam, patientId,
                    codePath, codeList);
            if (results.isEmpty()) {
                results = trySearch(fallbackClient, dataType, secondaryParam, patientId,
                        codePath, codeList);
            }

            // Cache results (including empty) to avoid repeat queries; evict if too large
            if (fallbackCache.size() < MAX_FALLBACK_CACHE_SIZE) {
                fallbackCache.put(cacheKey, results);
            }

            if (!results.isEmpty()) {
                log.debug("Fallback found {} {} resources for patient {}", results.size(), dataType, patientId);
            }
            return results;
        }

        /**
         * Collect CQL Code objects into a list for FHIR search parameter construction.
         */
        private List<org.opencds.cqf.cql.engine.runtime.Code> collectCodes(
                Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes) {
            if (codes == null) return null;
            List<org.opencds.cqf.cql.engine.runtime.Code> list = new ArrayList<>();
            codes.forEach(list::add);
            return list.isEmpty() ? null : list;
        }

        private List<Object> trySearch(IGenericClient client, String dataType, String paramName,
                String patientId, String codePath,
                List<org.opencds.cqf.cql.engine.runtime.Code> codes) {
            List<Object> results = new ArrayList<>();
            try {
                var search = client.search()
                        .forResource(dataType)
                        .where(new TokenClientParam(paramName).exactly().code(patientId));

                // Add code filter to the FHIR search query when available.
                // Use systemAndCode() so HAPI builds "system|code" properly
                // instead of escaping the pipe character.
                if (codePath != null && codes != null && !codes.isEmpty()) {
                    String searchParam = mapCodePathToSearchParam(dataType, codePath);
                    if (codes.size() == 1) {
                        org.opencds.cqf.cql.engine.runtime.Code c = codes.get(0);
                        if (c.getSystem() != null && !c.getSystem().isEmpty()) {
                            search = search.where(new TokenClientParam(searchParam)
                                    .exactly().systemAndCode(c.getSystem(), c.getCode()));
                        } else {
                            search = search.where(new TokenClientParam(searchParam)
                                    .exactly().code(c.getCode()));
                        }
                    } else {
                        // Multiple codes: build comma-separated system|code via whereMap
                        // to avoid HAPI escaping the pipe character
                        StringBuilder tokenValue = new StringBuilder();
                        for (org.opencds.cqf.cql.engine.runtime.Code c : codes) {
                            if (tokenValue.length() > 0) tokenValue.append(",");
                            if (c.getSystem() != null && !c.getSystem().isEmpty()) {
                                tokenValue.append(c.getSystem()).append("|");
                            }
                            tokenValue.append(c.getCode());
                        }
                        search = search.whereMap(java.util.Map.of(
                                searchParam, java.util.List.of(tokenValue.toString())));
                    }
                }

                Bundle bundle = search.returnBundle(Bundle.class).execute();

                // PAT-112c: upstream may embed OperationOutcome error in a 200 response.
                FhirErrorInspector.assertNoErrors(bundle, "fallback search " + dataType);

                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.getResource() != null) {
                            results.add(entry.getResource());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Fallback search {}?{}={} failed (upstream outage): {}",
                        dataType, paramName, patientId, e.getMessage());
                // PAT-112a: propagate. Previously this swallowed the exception and
                // returned an empty list, indistinguishable from "patient legitimately
                // has 0 matching records". A failing search must not look like absence
                // of data — CDS would render "no alert" and measure eval would undercount.
                throw new FhirServerUnavailableException(
                        "Fallback search " + dataType + "?" + paramName + "=" + patientId
                                + " failed: " + e.getMessage(), e);
            }
            return results;
        }

        /**
         * Map CQL codePath to the corresponding FHIR search parameter name.
         */
        private String mapCodePathToSearchParam(String dataType, String codePath) {
            // Most resources use "code" as both the CQL path and FHIR search param
            if ("code".equals(codePath)) {
                return "code";
            }
            if ("medication".equals(codePath) && "MedicationRequest".equals(dataType)) {
                return "code";
            }
            if ("vaccineCode".equals(codePath) && "Immunization".equals(dataType)) {
                return "vaccine-code";
            }
            // Default: use the codePath as-is
            return codePath;
        }
    }
}
