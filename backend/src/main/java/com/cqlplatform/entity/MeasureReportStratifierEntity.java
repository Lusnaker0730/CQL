package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stratification result row within a {@link MeasureReportGroupEntity}. Each stratum contains
 * its own populations (via {@link MeasureReportStratifierPopulationEntity}) and measure score.
 */
@Entity
@Table(name = "measure_report_stratifier")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportStratifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_report_group_id", nullable = false)
    private Long measureReportGroupId;

    @Column(name = "strata_id", nullable = false, length = 200)
    private String strataId;

    @Column(name = "strata_value", length = 500)
    private String strataValue;

    @Column(name = "measure_score")
    private Double measureScore;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
