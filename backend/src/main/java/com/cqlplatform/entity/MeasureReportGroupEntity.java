package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single population group within a {@link MeasureReportEntity}.
 *
 * <p>Part of Phase 1 of the measure_report normalization (ADR-001 / PAT-076).
 * Populated via dual-write at report save time + one-shot startup backfill for existing rows.
 * Consumers still read {@code MeasureReportEntity.resultJson} in Phase 1; Phase 2 migrates
 * them one service at a time.
 *
 * <p>ObservationStatistics (continuous-variable aggregates) is flattened into this row because
 * the relationship is always 1:1 with a group and the fields are all scalar.
 */
@Entity
@Table(name = "measure_report_group")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_report_id", nullable = false)
    private Long measureReportId;

    @Column(name = "group_id", nullable = false, length = 200)
    private String groupId;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "measure_score")
    private Double measureScore;

    @Column(name = "measure_score_unit", length = 100)
    private String measureScoreUnit;

    @Column(name = "total_patients")
    private Integer totalPatients;

    // ── Flattened ObservationStatistics (CV measures; null for proportion/ratio/cohort) ──
    @Column(name = "obs_aggregate_method", length = 50)
    private String obsAggregateMethod;

    @Column(name = "obs_aggregate_value")
    private Double obsAggregateValue;

    @Column(name = "obs_count")
    private Integer obsCount;

    @Column(name = "obs_minimum")
    private Double obsMinimum;

    @Column(name = "obs_maximum")
    private Double obsMaximum;

    @Column(name = "obs_average")
    private Double obsAverage;

    @Column(name = "obs_median")
    private Double obsMedian;

    @Column(name = "obs_unit", length = 100)
    private String obsUnit;

    /** Ordinal index within the parent report's group list (preserves order). */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
