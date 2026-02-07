package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.fhir.retrieve.RestFhirRetrieveProvider;
import org.opencds.cqf.cql.engine.fhir.searchparam.SearchParameterResolver;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        String serverUrl = resolveUrl(fhirServerUrl);
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

        R4FhirModelResolver modelResolver = new R4FhirModelResolver();
        RestFhirRetrieveProvider retrieveProvider = new RestFhirRetrieveProvider(
                searchParameterResolver,
                modelResolver,
                client);

        retrieveProvider.setTerminologyProvider(terminologyProvider);

        return new CountingRetrieveProvider(retrieveProvider, retrieveCount, fhirContext, resolveUrl(fhirServerUrl));
    }

    private String resolveUrl(String url) {
        return url != null ? url : defaultFhirServerUrl;
    }

    public Bundle searchResources(String fhirServerUrl, String resourceType, String searchParams) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be null or empty");
        }
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
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be null or empty");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or empty");
        }
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
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be null or empty");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or empty");
        }
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
            throw new RuntimeException("Failed to fetch patient list: " + e.getMessage(), e);
        }

        return patientIds;
    }

    public int getRetrieveCount() {
        return retrieveCount.get();
    }

    public int getAndResetRetrieveCount() {
        return retrieveCount.getAndSet(0);
    }

    private Map<String, List<String>> parseSearchParams(String searchParams) {
        Map<String, List<String>> params = new HashMap<>();
        if (searchParams == null || searchParams.isBlank()) {
            return params;
        }

        for (String param : searchParams.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2) {
                params.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
            }
        }
        return params;
    }

    private static class CountingRetrieveProvider implements RetrieveProvider {
        private static final Logger log = LoggerFactory.getLogger(CountingRetrieveProvider.class);

        private final RetrieveProvider delegate;
        private final AtomicInteger counter;
        private final IGenericClient fallbackClient;
        private final Map<String, List<Object>> cache = new HashMap<>();

        public CountingRetrieveProvider(RetrieveProvider delegate, AtomicInteger counter, FhirContext fhirContext,
                String fhirServerUrl) {
            this.delegate = delegate;
            this.counter = counter;
            this.fallbackClient = fhirContext.newRestfulGenericClient(fhirServerUrl);
        }

        @Override
        public Iterable<Object> retrieve(String context, String contextPath, Object contextValue,
                String dataType, String templateId, String codePath,
                Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes,
                String valueSet, String datePath, String dateLowPath,
                String dateHighPath, org.opencds.cqf.cql.engine.runtime.Interval dateRange) {

            String cacheKey = buildCacheKey(context, contextPath, contextValue, dataType,
                    templateId, codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);

            List<Object> cached = cache.get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for {} (key={}), returning {} cached results", dataType, cacheKey, cached.size());
                return cached;
            }

            log.debug("Retrieve called - dataType: {}, context: {}, contextPath: {}, contextValue: {}",
                    dataType, context, contextPath, contextValue);

            Iterable<Object> results = delegate.retrieve(context, contextPath, contextValue, dataType, templateId,
                    codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);

            List<Object> resultList = new ArrayList<>();
            if (results != null) {
                results.forEach(resultList::add);
            }

            if (resultList.isEmpty() && dataType != null && "Patient".equals(context) && contextValue != null) {
                log.debug("Delegate returned 0 results for {}. Attempting fallback search.", dataType);
                try {
                    String patientId = contextValue.toString();
                    if (patientId.startsWith("Patient/")) {
                        patientId = patientId.substring("Patient/".length());
                    }

                    Bundle bundle = fallbackClient.search()
                            .forResource(dataType)
                            .where(new TokenClientParam("patient").exactly().code(patientId))
                            .returnBundle(Bundle.class)
                            .execute();

                    if (bundle.hasEntry()) {
                        log.debug("Fallback search found {} entries for {}", bundle.getEntry().size(), dataType);
                        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                            resultList.add(entry.getResource());
                        }
                    } else {
                        log.debug("Fallback search also returned 0 entries for {}", dataType);
                    }
                } catch (Exception e) {
                    log.warn("Fallback search failed for {}: {}", dataType, e.getMessage(), e);
                }
            }

            log.debug("Final result count for {}: {}", dataType, resultList.size());
            cache.put(cacheKey, resultList);
            counter.addAndGet(resultList.size());
            return resultList;
        }

        private String buildCacheKey(String context, String contextPath, Object contextValue,
                String dataType, String templateId, String codePath,
                Iterable<org.opencds.cqf.cql.engine.runtime.Code> codes,
                String valueSet, String datePath, String dateLowPath,
                String dateHighPath, org.opencds.cqf.cql.engine.runtime.Interval dateRange) {
            StringBuilder sb = new StringBuilder();
            sb.append(dataType).append('|');
            sb.append(context).append('|');
            sb.append(contextPath).append('|');
            sb.append(contextValue).append('|');
            sb.append(templateId).append('|');
            sb.append(codePath).append('|');
            sb.append(valueSet).append('|');
            sb.append(datePath).append('|');
            sb.append(dateLowPath).append('|');
            sb.append(dateHighPath).append('|');
            sb.append(dateRange);
            if (codes != null) {
                sb.append('|');
                codes.forEach(c -> sb.append(c.getSystem()).append(':').append(c.getCode()).append(','));
            }
            return sb.toString();
        }
    }
}
