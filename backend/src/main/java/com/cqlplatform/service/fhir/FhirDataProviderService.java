package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.fhir.retrieve.RestFhirRetrieveProvider;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirDataProviderService {

    private final FhirContext fhirContext;

    @Value("${fhir.server.url:http://hapi.fhir.org/baseR4}")
    private String defaultFhirServerUrl;

    private final AtomicInteger retrieveCount = new AtomicInteger(0);

    public IGenericClient createClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);

        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseSummary(true);
        client.registerInterceptor(loggingInterceptor);

        return client;
    }

    public RetrieveProvider createDataProvider(String fhirServerUrl, TerminologyProvider terminologyProvider) {
        IGenericClient client = createClient(fhirServerUrl);
        SearchParameterResolver searchParameterResolver = new SearchParameterResolver(fhirContext);

        RestFhirRetrieveProvider retrieveProvider = new RestFhirRetrieveProvider(
                searchParameterResolver,
                client);

        retrieveProvider.setTerminologyProvider(terminologyProvider);

        return new CountingRetrieveProvider(retrieveProvider, retrieveCount);
    }

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
            throw new RuntimeException("FHIR search failed: " + e.getMessage(), e);
        }
    }

    public Resource getResource(String fhirServerUrl, String resourceType, String id) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.read()
                    .resource(resourceType)
                    .withId(id)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to read FHIR resource", e);
            throw new RuntimeException("FHIR read failed: " + e.getMessage(), e);
        }
    }

    public Resource createResource(String fhirServerUrl, Resource resource) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.create()
                    .resource(resource)
                    .execute()
                    .getResource();
        } catch (Exception e) {
            log.error("Failed to create FHIR resource", e);
            throw new RuntimeException("FHIR create failed: " + e.getMessage(), e);
        }
    }

    public Resource updateResource(String fhirServerUrl, Resource resource) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            return (Resource) client.update()
                    .resource(resource)
                    .execute()
                    .getResource();
        } catch (Exception e) {
            log.error("Failed to update FHIR resource", e);
            throw new RuntimeException("FHIR update failed: " + e.getMessage(), e);
        }
    }

    public void deleteResource(String fhirServerUrl, String resourceType, String id) {
        IGenericClient client = createClient(fhirServerUrl);
        try {
            client.delete()
                    .resourceById(resourceType, id)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to delete FHIR resource", e);
            throw new RuntimeException("FHIR delete failed: " + e.getMessage(), e);
        }
    }

    public java.util.List<String> getAllPatientIds(String fhirServerUrl) {
        IGenericClient client = createClient(fhirServerUrl);
        java.util.List<String> patientIds = new java.util.ArrayList<>();

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
            throw new RuntimeException("Failed to fetch patient list: " + e.getMessage(), e);
        }

        return patientIds;
    }

    public int getRetrieveCount() {
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

    private static class CountingRetrieveProvider implements RetrieveProvider {
        private final RetrieveProvider delegate;
        private final AtomicInteger counter;

        public CountingRetrieveProvider(RetrieveProvider delegate, AtomicInteger counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public Iterable<Object> retrieve(String context, String contextPath, Object contextValue,
                String dataType, String templateId, String codePath,
                Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes,
                String valueSet, String datePath, String dateLowPath,
                String dateHighPath, org.opencds.cqf.cql.engine.runtime.Interval dateRange) {
            counter.incrementAndGet();
            return delegate.retrieve(context, contextPath, contextValue, dataType, templateId,
                    codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);
        }

    }
}
