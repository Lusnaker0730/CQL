package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Population count within a {@link MeasureReportStratifierEntity} — same shape as
 * {@link MeasureReportPopulationEntity} but parented to a stratum rather than a group.
 *
 * <p>Kept separate (rather than reusing population table with an optional stratifier FK) to
 * preserve simple FK cascades and avoid nullable parent ambiguity.
 */
@Entity
@Table(name = "measure_report_stratifier_population")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportStratifierPopulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_report_stratifier_id", nullable = false)
    private Long measureReportStratifierId;

    @Column(name = "population_type", nullable = false, length = 50)
    private String populationType;

    @Column(name = "population_id", nullable = false, length = 200)
    private String populationId;

    @Column(name = "count", nullable = false)
    private Integer count;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
