package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MeasureReportRepository extends JpaRepository<MeasureReportEntity, Long> {

    List<MeasureReportEntity> findByMeasureDefinitionIdOrderByCreatedAtDesc(Long measureDefinitionId);

    /**
     * PAT-146 — bounded variant. The unpaged overload above is preserved for
     * existing callers that need the full set (e.g. cleanup / cascade), but new
     * read paths exposed to controllers should always pass a {@link Pageable}
     * with a hard upper bound; a daily-evaluated measure can accumulate
     * thousands of reports and serializing the entire collection is an
     * unbounded-response OOM / DoS vector.
     */
    List<MeasureReportEntity> findByMeasureDefinitionIdOrderByCreatedAtDesc(
            Long measureDefinitionId, Pageable pageable);

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

    // BUG-136 — dashboard reads. Reports carry evaluation scores over PHI, so every
    // dashboard query is tenant-scoped; the unscoped predecessors of these four were
    // reachable from /api/measures/dashboard/* with no gate at all.

    List<MeasureReportEntity> findTop10ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.tenantId = :tenantId " +
            "AND r.createdAt IS NOT NULL " +
            "AND (:measureId IS NULL OR r.measureDefinitionId = :measureId) " +
            "ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findRecentByOptionalMeasure(
            @Param("tenantId") Long tenantId,
            @Param("measureId") Long measureId,
            org.springframework.data.domain.Pageable pageable);

    List<MeasureReportEntity> findByTenantIdAndDepartmentOrderByCreatedAtDesc(Long tenantId, String department);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.tenantId = :tenantId " +
            "AND r.measureDefinitionId = :measureId " +
            "AND r.createdAt = (SELECT MAX(r2.createdAt) FROM MeasureReportEntity r2 " +
            "WHERE r2.tenantId = :tenantId AND r2.measureDefinitionId = :measureId)")
    List<MeasureReportEntity> findLatestByMeasureDefinitionId(@Param("tenantId") Long tenantId,
                                                              @Param("measureId") Long measureId);

    // Phase 2 — tenant-scoped MANAGEMENT queries (reports carry PHI; must not leak cross-tenant).
    java.util.Optional<MeasureReportEntity> findByIdAndTenantId(Long id, Long tenantId);

    List<MeasureReportEntity> findTop50ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<MeasureReportEntity> findByTenantIdAndMeasureDefinitionIdOrderByCreatedAtDesc(
            Long tenantId, Long measureDefinitionId, Pageable pageable);

    List<MeasureReportEntity> findByTenantIdAndMeasureNameOrderByCreatedAtDesc(Long tenantId, String measureName);

    List<MeasureReportEntity> findByTenantIdAndMeasureNameOrderByPeriodStartAsc(Long tenantId, String measureName);

    List<MeasureReportEntity> findByTenantIdAndMeasureNameAndPeriodStartAndPeriodEnd(
            Long tenantId, String measureName, LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.tenantId = :tenantId AND r.measureName = :measureName " +
            "AND r.periodStart >= :rangeStart AND r.periodEnd <= :rangeEnd ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findByTenantIdAndMeasureNameAndPeriodOverlap(
            @Param("tenantId") Long tenantId, @Param("measureName") String measureName,
            @Param("rangeStart") LocalDate rangeStart, @Param("rangeEnd") LocalDate rangeEnd);

    List<MeasureReportEntity> findByTenantIdAndMeasureDefinitionIdAndPeriodStartAndPeriodEnd(
            Long tenantId, Long measureDefinitionId, LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT r FROM MeasureReportEntity r WHERE r.tenantId = :tenantId AND r.measureDefinitionId = :measureId " +
            "AND r.periodStart <= :rangeEnd AND r.periodEnd >= :rangeStart ORDER BY r.createdAt DESC")
    List<MeasureReportEntity> findByTenantIdAndMeasureDefinitionIdAndPeriodOverlap(
            @Param("tenantId") Long tenantId, @Param("measureId") Long measureId,
            @Param("rangeStart") LocalDate rangeStart, @Param("rangeEnd") LocalDate rangeEnd);

    List<MeasureReportEntity> findByTenantIdAndMeasureDefinitionIdOrderByPeriodStartAsc(
            Long tenantId, Long measureDefinitionId);
}
