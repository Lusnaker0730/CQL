package com.cqlplatform.model.measure;

import com.cqlplatform.security.NoXss;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 200)
    @NoXss
    private String title;

    @Size(max = 2000)
    @NoXss
    private String description;

    /** FHIR Patient Bundle JSON — exempt from XSS sanitization. */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String patientBundleJson;

    /** Expected population membership: e.g. {"initial-population": true, "denominator": true, "numerator": false} */
    private Map<String, Boolean> expectedPopulations;

    /** Status: pass, fail, error, pending */
    @Builder.Default
    @Pattern(regexp = "pass|fail|error|pending")
    private String status = "pending";

    /** JSON of the last execution result */
    private String lastRunResultJson;

    /** Last run actual populations: e.g. {"initial-population": true, "denominator": true, "numerator": true} */
    private Map<String, Boolean> lastRunActualPopulations;

    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Series name for grouping test cases */
    @Size(max = 100)
    @NoXss
    private String series;

    /** Sort order within series */
    @Builder.Default
    private Integer sortOrder = 0;
}
