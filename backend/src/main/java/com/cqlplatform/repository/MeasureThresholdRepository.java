package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureThresholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BUG-136 — tenant is derived by joining the parent measure, not from a column on
 * measure_threshold: measure_definition_id is NOT NULL REFERENCES measure_definition
 * ON DELETE CASCADE (V27), so a threshold's tenant is definitionally its measure's.
 * Denormalising it would create a second source of truth that can drift — same reasoning
 * as test_case (BUG-133) and cds_external_cql_library (BUG-134).
 *
 * <p>Note {@code department} is a denormalised string here, and department codes are only
 * unique per tenant since V66 — so the old findByDepartmentAndActiveTrue matched other
 * tenants' thresholds whenever two clinics used the same code.
 */
@Repository
public interface MeasureThresholdRepository extends JpaRepository<MeasureThresholdEntity, Long> {

    /**
     * Scoped by the caller gating on the parent measure first (MeasureController.requireMeasure),
     * which resolves through the tenant-scoped findByIdAndTenantId.
     */
    List<MeasureThresholdEntity> findByMeasureDefinitionIdAndActiveTrue(Long measureDefinitionId);

    @Query("SELECT t FROM MeasureThresholdEntity t, MeasureDefinitionEntity m "
            + "WHERE t.measureDefinitionId = m.id AND m.tenantId = :tenantId AND t.active = true")
    List<MeasureThresholdEntity> findActiveByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT t FROM MeasureThresholdEntity t, MeasureDefinitionEntity m "
            + "WHERE t.measureDefinitionId = m.id AND m.tenantId = :tenantId "
            + "AND t.department = :department AND t.active = true")
    List<MeasureThresholdEntity> findActiveByTenantIdAndDepartment(@Param("tenantId") Long tenantId,
                                                                   @Param("department") String department);
}
