package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MeasureReportRepository extends JpaRepository<MeasureReportEntity, Long> {

    List<MeasureReportEntity> findByMeasureDefinitionIdOrderByCreatedAtDesc(Long measureDefinitionId);

    List<MeasureReportEntity> findByMeasureNameOrderByCreatedAtDesc(String measureName);

    List<MeasureReportEntity> findByMeasureNameOrderByPeriodStartAsc(String measureName);

    List<MeasureReportEntity> findByMeasureNameAndPeriodStartAndPeriodEnd(
            String measureName, LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.measureName = :measureName " +
            "AND r.periodStart >= :rangeStart AND r.periodEnd <= :rangeEnd " +
            "ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findByMeasureNameAndPeriodOverlap(
            @Param("measureName") String measureName,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    List<MeasureReportEntity> findByMeasureDefinitionIdAndPeriodStartAndPeriodEnd(
            Long measureDefinitionId, LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.measureDefinitionId = :measureId " +
            "AND r.periodStart <= :rangeEnd AND r.periodEnd >= :rangeStart " +
            "ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findByMeasureDefinitionIdAndPeriodOverlap(
            @Param("measureId") Long measureId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    List<MeasureReportEntity> findByMeasureDefinitionIdOrderByPeriodStartAsc(Long measureDefinitionId);

    List<MeasureReportEntity> findTop50ByOrderByCreatedAtDesc();

    List<MeasureReportEntity> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.createdAt IS NOT NULL " +
            "AND (:measureId IS NULL OR r.measureDefinitionId = :measureId) " +
            "ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findRecentByOptionalMeasure(
            @Param("measureId") Long measureId,
            org.springframework.data.domain.Pageable pageable);

    List<MeasureReportEntity> findByDepartmentOrderByCreatedAtDesc(String department);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.measureDefinitionId = :measureId " +
            "AND r.createdAt = (SELECT MAX(r2.createdAt) FROM MeasureReportEntity r2 " +
            "WHERE r2.measureDefinitionId = :measureId)")
    List<MeasureReportEntity> findLatestByMeasureDefinitionId(@Param("measureId") Long measureId);
}
