package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportPopulationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeasureReportPopulationRepository extends JpaRepository<MeasureReportPopulationEntity, Long> {

    List<MeasureReportPopulationEntity> findByMeasureReportGroupIdOrderByOrdinalAsc(Long measureReportGroupId);

    /**
     * Fetch all populations for every group of a given report in one query (group ordinal, then
     * population ordinal). Used by consumers that need the full population map per report —
     * avoids N+1 calls of {@link #findByMeasureReportGroupIdOrderByOrdinalAsc(Long)}.
     */
    @Query("SELECT p FROM MeasureReportPopulationEntity p " +
            "WHERE p.measureReportGroupId IN " +
            "   (SELECT g.id FROM MeasureReportGroupEntity g WHERE g.measureReportId = :reportId) " +
            "ORDER BY p.measureReportGroupId ASC, p.ordinal ASC")
    List<MeasureReportPopulationEntity> findAllForReport(@Param("reportId") Long reportId);
}
