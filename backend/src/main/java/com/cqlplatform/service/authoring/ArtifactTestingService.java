package com.cqlplatform.service.authoring;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.service.cql.CqlExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for testing CDS artifacts against patient data.
 * Generates CQL from the artifact, then executes it against provided patient bundles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactTestingService {

    private final CqlGenerationService cqlGenerationService;
    private final CqlExecutionService cqlExecutionService;

    /**
     * Test an artifact against a list of patient IDs.
     * Returns per-patient results showing inclusion/exclusion/subpopulation/recommendation status.
     */
    public Map<String, Object> testArtifact(Long artifactId, List<String> patientIds, String fhirServerUrl) {
        // Generate CQL from the artifact
        String cql = cqlGenerationService.generateCql(artifactId);

        List<Map<String, Object>> patientResults = new ArrayList<>();

        for (String patientId : patientIds) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("patientId", patientId);

            try {
                CqlExecutionRequest request = new CqlExecutionRequest();
                request.setCql(cql);
                request.setPatientId(patientId);
                request.setFhirServerUrl(fhirServerUrl);

                CqlExecutionResponse response = cqlExecutionService.execute(request);

                if (response.isSuccess() && response.getResults() != null) {
                    Map<String, Object> expressions = new LinkedHashMap<>();
                    response.getResults().forEach((name, exprResult) -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("value", exprResult.getDisplayValue());
                        entry.put("type", exprResult.getValueType());
                        expressions.put(name, entry);
                    });
                    result.put("success", true);
                    result.put("expressions", expressions);

                    // Extract key results for summary
                    if (response.getResults().containsKey("MeetsInclusionCriteria")) {
                        result.put("meetsInclusion", response.getResults().get("MeetsInclusionCriteria").getDisplayValue());
                    }
                    if (response.getResults().containsKey("MeetsExclusionCriteria")) {
                        result.put("meetsExclusion", response.getResults().get("MeetsExclusionCriteria").getDisplayValue());
                    }
                    if (response.getResults().containsKey("InPopulation")) {
                        result.put("inPopulation", response.getResults().get("InPopulation").getDisplayValue());
                    }
                    if (response.getResults().containsKey("Recommendation")) {
                        result.put("recommendation", response.getResults().get("Recommendation").getDisplayValue());
                    }
                } else {
                    result.put("success", false);
                    result.put("error", "Execution failed");
                }
            } catch (Exception e) {
                log.error("Error testing artifact {} for patient {}: {}", artifactId, patientId, e.getMessage());
                result.put("success", false);
                result.put("error", e.getMessage());
            }

            patientResults.add(result);
        }

        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("artifactId", artifactId);
        testResult.put("cql", cql);
        testResult.put("patientResults", patientResults);
        testResult.put("totalPatients", patientIds.size());
        testResult.put("successCount", patientResults.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count());
        return testResult;
    }
}
