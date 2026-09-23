package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * PAT-234 — one (supplemental data element, value) row of a measure report: how many
 * evaluated patients had that value. A row with a {@code null} value carries the patients that
 * had no value at all. Aggregates only, never patient ids; tenant scope is inherited from
 * {@code measure_report} through the RLS policy (V73).
 */
@Entity
@Table(name = "measure_report_supplemental_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportSupplementalDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_report_id", nullable = false)
    private Long measureReportId;

    /** The CQL define name. */
    @Column(name = "definition", nullable = false, length = 500)
    private String definition;

    /** {@code supplemental-data} or {@code risk-adjustment-factor}. */
    @Column(name = "usage", nullable = false, length = 50)
    private String usage;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** {@code null} = the "no value" row. */
    @Column(name = "value", length = 500)
    private String value;

    @Column(name = "count", nullable = false)
    private Integer count;

    /** Position within the report: elements in declaration order, values in first-seen order. */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}
