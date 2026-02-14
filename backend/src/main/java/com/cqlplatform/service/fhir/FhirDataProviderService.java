package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.fhir.retrieve.RestFhirRetrieveProvider;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirDataProviderService {

    private final FhirContext fhirContext;

    @Value("${fhir.server.url:http://hapi-fhir:8080/fhir}")
    private String defaultFhirServerUrl;

    private final AtomicInteger retrieveCount = new AtomicInteger(0);

    public IGenericClient createClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        log.debug("Creating FHIR Client for URL: {}", serverUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);

        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseSummary(true);
        client.registerInterceptor(loggingInterceptor);

        return client;
    }

    public RetrieveProvider createDataProvider(String fhirServerUrl, TerminologyProvider terminologyProvider) {
        log.debug("Creating RetrieveProvider for URL: {}", fhirServerUrl);
        IGenericClient client = createClient(fhirServerUrl);
        SearchParameterResolver searchParameterResolver = new SearchParameterResolver(fhirContext);

        try {
            log.debug("Resolver check - Observation.patient path: {}",
                    searchParameterResolver.getSearchParameterDefinition("Observation", "patient").getPath());
        } catch (Exception e) {
            log.debug("Resolver check failed: {}", e.getMessage());
        }

        RestFhirRetrieveProvider retrieveProvider = new RestFhirRetrieveProvider(
                searchParameterResolver,
                client);

        retrieveProvider.setTerminologyProvider(terminologyProvider);

        return new CountingRetrieveProvider(retrieveProvider, retrieveCount, fhirContext, processUrl(fhirServerUrl));
    }

    private String processUrl(String url) {
        return url != null ? url : defaultFhirServerUrl;
    }

    @CircuitBreaker(name = "fhirDataProvider", fallbackMethod = "searchResourcesFallback")
    @Retry(name = "fhirDataProvider")
    public Bundle searchResources(String fhirServerUrl, String resourceType, String searchParams) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return client.search()
                    .forResource(resourceType)
                    .whereMap(parseSearchParams(searchParams))
                    .returnBundle(Bundle.class)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to search FHIR resources", e);
            throw new FhirServerUnavailableException("FHIR search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private Bundle searchResourcesFallback(String fhirServerUrl, String resourceType, String searchParams, Throwable t) {
        log.warn("Circuit breaker fallback for searchResources: {}", t.getMessage());
        return new Bundle();
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

                if (bundle.getLink(Bundle.LINK_NEXT) != null) {
                    bundle = client.loadPage().next(bundle).execute();
                } else {
                    bundle = null;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch all patients", e);
            throw new FhirServerUnavailableException("Failed to fetch patient list: " + e.getMessage(), e);
        }

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
        return new Bundle();
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
            "SupplyRequest", "VisionPrescription", "Account"
    );

    private static class CountingRetrieveProvider implements RetrieveProvider {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CountingRetrieveProvider.class);

        private final RetrieveProvider delegate;
        private final AtomicInteger counter;
        private final FhirContext fhirContext;
        private final String fhirServerUrl;

        public CountingRetrieveProvider(RetrieveProvider delegate, AtomicInteger counter, FhirContext fhirContext,
                String fhirServerUrl) {
            this.delegate = delegate;
            this.counter = counter;
            this.fhirContext = fhirContext;
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

            Iterable<Object> results;
            try {
                results = delegate.retrieve(context, contextPath, contextValue, dataType, templateId,
                        codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);
            } catch (Exception e) {
                log.debug("Delegate retrieve threw exception for {}: {}", dataType, e.getMessage());
                results = null;
            }

            List<Object> resultList = new ArrayList<>();
            if (results != null) {
                results.forEach(resultList::add);
            }

            // MANUAL FALLBACK: If delegate returned 0 results, try manual FHIR client
            if (resultList.isEmpty() && contextValue != null && "Patient".equals(context)) {
                String patientId = contextValue.toString();
                if (patientId.startsWith("Patient/")) {
                    patientId = patientId.substring("Patient/".length());
                }

                if ("Patient".equals(dataType)) {
                    // Fallback: read Patient by ID directly
                    log.debug("Fallback: reading Patient/{}", patientId);
                    try {
                        IGenericClient fallbackClient = fhirContext.newRestfulGenericClient(fhirServerUrl);
                        Patient patient = fallbackClient.read().resource(Patient.class).withId(patientId).execute();
                        if (patient != null) {
                            resultList.add(patient);
                        }
                    } catch (Exception e) {
                        log.debug("Patient fallback read failed: {}", e.getMessage());
                    }
                } else {
                    // Generic fallback: search by subject or patient parameter
                    resultList.addAll(fallbackSearch(dataType, patientId));
                }
            }

            int currentCount = resultList.size();
            log.debug("Final result count for {}: {}", dataType, currentCount);

            counter.addAndGet(currentCount);
            return resultList;
        }

        /**
         * Generic fallback search: tries 'subject', then 'patient' search parameter.
         */
        private List<Object> fallbackSearch(String dataType, String patientId) {
            log.debug("Fallback search for {}?subject/patient={}", dataType, patientId);
            IGenericClient fallbackClient = fhirContext.newRestfulGenericClient(fhirServerUrl);

            // Determine which search parameter to try first
            String primaryParam = SUBJECT_BASED_RESOURCES.contains(dataType) ? "subject" : "patient";
            String secondaryParam = "subject".equals(primaryParam) ? "patient" : "subject";

            List<Object> results = trySearch(fallbackClient, dataType, primaryParam, patientId);
            if (results.isEmpty()) {
                results = trySearch(fallbackClient, dataType, secondaryParam, patientId);
            }

            if (!results.isEmpty()) {
                log.debug("Fallback found {} {} resources for patient {}", results.size(), dataType, patientId);
            }
            return results;
        }

        private List<Object> trySearch(IGenericClient client, String dataType, String paramName, String patientId) {
            List<Object> results = new ArrayList<>();
            try {
                Bundle bundle = client.search()
                        .forResource(dataType)
                        .where(new TokenClientParam(paramName).exactly().code(patientId))
                        .returnBundle(Bundle.class)
                        .execute();

                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.getResource() != null) {
                            results.add(entry.getResource());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Fallback search {}?{}={} failed: {}", dataType, paramName, patientId, e.getMessage());
            }
            return results;
        }
    }
}
