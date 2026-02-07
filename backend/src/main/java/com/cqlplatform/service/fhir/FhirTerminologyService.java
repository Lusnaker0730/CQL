package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.fhir.terminology.R4FhirTerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirTerminologyService {

    private final FhirContext fhirContext;

    @Value("${fhir.terminology.url:http://tx.fhir.org/r4}")
    private String defaultTerminologyServerUrl;

    public TerminologyProvider createTerminologyProvider(String terminologyServerUrl) {
        String serverUrl = terminologyServerUrl != null ? terminologyServerUrl : defaultTerminologyServerUrl;
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
        return new R4FhirTerminologyProvider(client);
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

            Parameters result = client.operation()
                    .onType(ValueSet.class)
                    .named("$expand")
                    .withParameters(params)
                    .execute();

            return (ValueSet) result.getParameter("return").getResource();
        } catch (Exception e) {
            log.error("Failed to expand ValueSet: {}", valueSetUrl, e);
            throw new RuntimeException("ValueSet expansion failed: " + e.getMessage(), e);
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
            return false;
        }
    }

    @SuppressWarnings("unused")
    private boolean validateCodeFallback(String system, String code, String valueSetUrl, Throwable t) {
        log.warn("Circuit breaker fallback for validateCode: {}", t.getMessage());
        return false;
    }

    @Cacheable(value = "codeLookup", key = "#system + ':' + #code")
    @CircuitBreaker(name = "fhirTerminology", fallbackMethod = "lookupCodeFallback")
    @Retry(name = "fhirTerminology")
    public CodeLookupResult lookupCode(String system, String code) {
        log.debug("Looking up code {} from {}", code, system);
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
            throw new RuntimeException("Code lookup failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private CodeLookupResult lookupCodeFallback(String system, String code, Throwable t) {
        log.warn("Circuit breaker fallback for lookupCode: {}", t.getMessage());
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
            throw new RuntimeException("ValueSet search failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private List<ValueSet> searchValueSetsFallback(String searchTerm, Throwable t) {
        log.warn("Circuit breaker fallback for searchValueSets: {}", t.getMessage());
        return new ArrayList<>();
    }

    public record CodeLookupResult(
            String system,
            String code,
            String name,
            String display,
            List<String> designations
    ) {}
}
