package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One population row within a {@link MeasureReportGroupEntity} (initial-population /
 * numerator / denominator / measure-population / *-exclusion / *-exception).
 *
 * <p>Enables SQL queries like "reports where denominator &gt; 0 and numerator &lt; denominator/2
 * in department X" without application-level JSON parsing.
 */
@Entity
@Table(name = "measure_report_population")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportPopulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_report_group_id", nullable = false)
    private Long measureReportGroupId;

    /** e.g. "initial-population", "numerator", "denominator", "measure-population". */
    @Column(name = "population_type", nullable = false, length = 50)
    private String populationType;

    @Column(name = "population_id", nullable = false, length = 200)
    private String populationId;

    @Column(name = "count", nullable = false)
    private Integer count;

    /** Patient IDs belonging to this population, as JSON array. Rarely queried individually. */
    @Column(name = "subject_ids_json", columnDefinition = "TEXT")
    private String subjectIdsJson;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
