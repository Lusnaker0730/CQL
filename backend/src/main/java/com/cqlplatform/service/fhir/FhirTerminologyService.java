package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
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

    @Cacheable(value = "codeValidation", key = "#system + ':' + #code + ':' + #valueSetUrl")
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

    @Cacheable(value = "codeLookup", key = "#system + ':' + #code")
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

    public record CodeLookupResult(
            String system,
            String code,
            String name,
            String display,
            List<String> designations
    ) {}
}
