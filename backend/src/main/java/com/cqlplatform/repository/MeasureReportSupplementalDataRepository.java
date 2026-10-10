package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportSupplementalDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** PAT-234 — distribution rows of a report; always reached through the report's id (tenant-scoped by RLS). */
public interface MeasureReportSupplementalDataRepository extends JpaRepository<MeasureReportSupplementalDataEntity, Long> {

    List<MeasureReportSupplementalDataEntity> findByMeasureReportIdOrderByOrdinalAsc(Long measureReportId);

    /** Bulk delete (used before re-persisting during dual-write). */
    @Modifying
    @Query("DELETE FROM MeasureReportSupplementalDataEntity s WHERE s.measureReportId = :measureReportId")
    int deleteAllByMeasureReportId(@Param("measureReportId") Long measureReportId);
}
