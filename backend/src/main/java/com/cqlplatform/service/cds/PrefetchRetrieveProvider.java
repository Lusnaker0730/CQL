package com.cqlplatform.service.cds;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.ValueSetInfo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory RetrieveProvider that serves FHIR resources from CDS Hooks prefetch data.
 * Supports terminology-aware retrieval: when a ValueSet URL is provided, expands it
 * via the TerminologyProvider to filter resources by code.
 */
@Slf4j
public class PrefetchRetrieveProvider implements RetrieveProvider {

    private final Map<String, List<Resource>> resourcesByType = new HashMap<>();
    private TerminologyProvider terminologyProvider;

    public PrefetchRetrieveProvider(List<Resource> resources, String patientId) {
        for (Resource resource : resources) {
            resourcesByType
                    .computeIfAbsent(resource.fhirType(), k -> new ArrayList<>())
                    .add(resource);
        }
        log.info("PrefetchRetrieveProvider initialized with {} resources for patient {}",
                resources.size(), patientId);
        resourcesByType.forEach((type, list) ->
                log.debug("  {} {} resource(s)", list.size(), type));
    }

    /**
     * Merge additional resources (e.g. from draftOrders or resolved prefetch templates)
     * into the existing resource map.
     */
    public void addResources(List<Resource> newResources) {
        for (Resource resource : newResources) {
            resourcesByType
                    .computeIfAbsent(resource.fhirType(), k -> new ArrayList<>())
                    .add(resource);
        }
        log.info("Added {} resources to PrefetchRetrieveProvider", newResources.size());
    }

    /**
     * Set a TerminologyProvider for ValueSet expansion during retrieves.
     * Without this, ValueSet-based retrieves (e.g. [Condition: "Diabetes"]) will
     * return all resources of the matching type without code filtering.
     */
    public void setTerminologyProvider(TerminologyProvider terminologyProvider) {
        this.terminologyProvider = terminologyProvider;
    }

    @Override
    public Iterable<Object> retrieve(String context, String contextPath, Object contextValue,
            String dataType, String templateId, String codePath,
            Iterable<Code> codes, String valueSet, String datePath,
            String dateLowPath, String dateHighPath, Interval dateRange) {

        // If the CQL engine passed a ValueSet URL but no expanded codes,
        // expand the ValueSet via the TerminologyProvider to get codes for filtering.
        if (codes == null && valueSet != null && !valueSet.isBlank() && terminologyProvider != null) {
            log.info("Expanding ValueSet for retrieve: {}", valueSet);
            try {
                codes = terminologyProvider.expand(new ValueSetInfo().withId(valueSet));
            } catch (Exception e) {
                log.warn("Failed to expand ValueSet {}: {}", valueSet, e.getMessage());
            }
        }

        List<String> codeStrs = new ArrayList<>();
        if (codes != null) {
            for (Code code : codes) {
                codeStrs.add(code.getCode());
            }
        }
        log.info("Retrieve: dataType={}, codePath={}, codes={}, valueSet={}, context={}, contextPath={}, contextValue={}",
                dataType, codePath, codeStrs, valueSet, context, contextPath, contextValue);

        List<Resource> candidates = resourcesByType.getOrDefault(dataType, Collections.emptyList());
        int beforeFilter = candidates.size();

        // Filter by code if specified
        if (codePath != null && !codeStrs.isEmpty()) {
            Set<String> codeValues = new HashSet<>(codeStrs);
            candidates = candidates.stream()
                    .filter(r -> matchesCode(r, codePath, codeValues))
                    .collect(Collectors.toList());
        }

        log.info("Retrieve result: {} {} resources (before code filter: {})", candidates.size(), dataType, beforeFilter);
        return new ArrayList<>(candidates);
    }

    private boolean matchesCode(Resource resource, String codePath, Set<String> codes) {
        if (resource instanceof Observation obs) {
            if ("code".equals(codePath) && obs.hasCode()) {
                for (Coding coding : obs.getCode().getCoding()) {
                    if (codes.contains(coding.getCode())) {
                        return true;
                    }
                }
            }
        } else if (resource instanceof Condition cond) {
            if ("code".equals(codePath) && cond.hasCode()) {
                for (Coding coding : cond.getCode().getCoding()) {
                    if (codes.contains(coding.getCode())) {
                        return true;
                    }
                }
            }
        } else if (resource instanceof MedicationRequest medReq) {
            if ("medication".equals(codePath) && medReq.hasMedicationCodeableConcept()) {
                for (Coding coding : medReq.getMedicationCodeableConcept().getCoding()) {
                    if (codes.contains(coding.getCode())) {
                        return true;
                    }
                }
            }
        } else if (resource instanceof ServiceRequest svcReq) {
            if ("code".equals(codePath) && svcReq.hasCode()) {
                for (Coding coding : svcReq.getCode().getCoding()) {
                    if (codes.contains(coding.getCode())) {
                        return true;
                    }
                }
            }
        }
        // For resources without code filtering or unknown code paths, include them
        return codes.isEmpty();
    }
}
