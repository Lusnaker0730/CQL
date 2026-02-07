package com.cqlplatform.service.cds;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory RetrieveProvider that serves FHIR resources from CDS Hooks prefetch data.
 */
@Slf4j
public class PrefetchRetrieveProvider implements RetrieveProvider {

    private final Map<String, List<Resource>> resourcesByType = new HashMap<>();
    private final String patientId;

    public PrefetchRetrieveProvider(List<Resource> resources, String patientId) {
        this.patientId = patientId;
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

    @Override
    public Iterable<Object> retrieve(String context, String contextPath, Object contextValue,
            String dataType, String templateId, String codePath,
            Iterable<Code> codes, String valueSet, String datePath,
            String dateLowPath, String dateHighPath, Interval dateRange) {

        log.debug("Retrieve called: dataType={}, codePath={}, codes={}", dataType, codePath, codes);

        List<Resource> candidates = resourcesByType.getOrDefault(dataType, Collections.emptyList());

        // Filter by code if specified
        if (codePath != null && codes != null) {
            Set<String> codeValues = new HashSet<>();
            for (Code code : codes) {
                codeValues.add(code.getCode());
            }
            candidates = candidates.stream()
                    .filter(r -> matchesCode(r, codePath, codeValues))
                    .collect(Collectors.toList());
        }

        log.debug("Returning {} {} resources", candidates.size(), dataType);
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
        }
        // For resources without code filtering or unknown code paths, include them
        return codes.isEmpty();
    }
}
