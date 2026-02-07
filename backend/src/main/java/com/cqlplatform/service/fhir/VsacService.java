package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class VsacService {

    private final FhirContext fhirContext;
    private final String vsacApiUrl;
    private final String vsacApiKey;

    public VsacService(FhirContext fhirContext,
                       @Value("${vsac.api.url:https://cts.nlm.nih.gov/fhir/}") String vsacApiUrl,
                       @Value("${vsac.api.key:}") String vsacApiKey) {
        this.fhirContext = fhirContext;
        this.vsacApiUrl = vsacApiUrl;
        this.vsacApiKey = vsacApiKey;
    }

    private IGenericClient createVsacClient() {
        IGenericClient client = fhirContext.newRestfulGenericClient(vsacApiUrl);
        if (vsacApiKey != null && !vsacApiKey.isBlank()) {
            client.registerInterceptor(new BasicAuthInterceptor("apikey", vsacApiKey));
        }
        return client;
    }

    @Cacheable(value = "vsacValueSets", key = "#oid")
    @CircuitBreaker(name = "vsacService", fallbackMethod = "getValueSetByOidFallback")
    @Retry(name = "vsacService")
    public ValueSet getValueSetByOid(String oid) {
        log.info("Fetching ValueSet from VSAC by OID: {}", oid);
        IGenericClient client = createVsacClient();

        try {
            return client.read()
                    .resource(ValueSet.class)
                    .withId(oid)
                    .execute();
        } catch (Exception e) {
            log.error("Failed to fetch ValueSet from VSAC: {}", oid, e);
            throw new RuntimeException("VSAC ValueSet fetch failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private ValueSet getValueSetByOidFallback(String oid, Throwable t) {
        log.warn("Circuit breaker fallback for getValueSetByOid: {}", t.getMessage());
        throw new FhirServerUnavailableException("VSAC unavailable: " + t.getMessage(), t);
    }

    @Cacheable(value = "vsacValueSets", key = "'expand:' + #oid")
    @CircuitBreaker(name = "vsacService", fallbackMethod = "expandValueSetByOidFallback")
    @Retry(name = "vsacService")
    public ValueSet expandValueSetByOid(String oid) {
        log.info("Expanding ValueSet from VSAC by OID: {}", oid);
        IGenericClient client = createVsacClient();

        try {
            Parameters params = new Parameters();
            params.addParameter("url", new UriType("http://cts.nlm.nih.gov/fhir/ValueSet/" + oid));

            Parameters result = client.operation()
                    .onType(ValueSet.class)
                    .named("$expand")
                    .withParameters(params)
                    .execute();

            return (ValueSet) result.getParameter("return").getResource();
        } catch (Exception e) {
            log.error("Failed to expand ValueSet from VSAC: {}", oid, e);
            throw new RuntimeException("VSAC ValueSet expansion failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private ValueSet expandValueSetByOidFallback(String oid, Throwable t) {
        log.warn("Circuit breaker fallback for expandValueSetByOid: {}", t.getMessage());
        throw new FhirServerUnavailableException("VSAC unavailable: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "vsacService", fallbackMethod = "searchValueSetsFallback")
    @Retry(name = "vsacService")
    public List<ValueSet> searchValueSets(String searchTerm) {
        log.info("Searching VSAC ValueSets: {}", searchTerm);
        IGenericClient client = createVsacClient();

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
            log.error("Failed to search VSAC ValueSets", e);
            throw new RuntimeException("VSAC search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private List<ValueSet> searchValueSetsFallback(String searchTerm, Throwable t) {
        log.warn("Circuit breaker fallback for searchValueSets: {}", t.getMessage());
        return new ArrayList<>();
    }
}
