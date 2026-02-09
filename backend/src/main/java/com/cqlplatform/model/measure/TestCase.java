package com.cqlplatform.model.measure;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {

    private Long id;
    private Long measureDefinitionId;
    private String title;
    private String description;

    /** FHIR Patient Bundle JSON — the test patient data */
    private String patientBundleJson;

    /** Expected population membership: e.g. {"initial-population": true, "denominator": true, "numerator": false} */
    private Map<String, Boolean> expectedPopulations;

    /** Status: pass, fail, error, pending */
    @Builder.Default
    private String status = "pending";

    /** JSON of the last execution result */
    private String lastRunResultJson;

    /** Last run actual populations: e.g. {"initial-population": true, "denominator": true, "numerator": true} */
    private Map<String, Boolean> lastRunActualPopulations;

    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Series name for grouping test cases */
    private String series;

    /** Sort order within series */
    @Builder.Default
    private Integer sortOrder = 0;
}
