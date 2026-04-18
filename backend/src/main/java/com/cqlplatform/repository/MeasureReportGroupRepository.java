package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeasureReportGroupRepository extends JpaRepository<MeasureReportGroupEntity, Long> {

    /** Ordered by {@code ordinal} so consumers see groups in the original evaluation order. */
    List<MeasureReportGroupEntity> findByMeasureReportIdOrderByOrdinalAsc(Long measureReportId);

    /** Bulk delete (used before re-persisting during dual-write). */
    @Modifying
    @Query("DELETE FROM MeasureReportGroupEntity g WHERE g.measureReportId = :measureReportId")
    int deleteAllByMeasureReportId(@Param("measureReportId") Long measureReportId);

    /** Used by the startup backfill: reports whose normalized groups haven't been populated yet. */
    @Query("SELECT DISTINCT g.measureReportId FROM MeasureReportGroupEntity g")
    List<Long> findDistinctMeasureReportIdsAlreadyBackfilled();
}
