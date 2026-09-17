package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsServiceAnalyticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsServiceAnalyticsRepository extends JpaRepository<CdsServiceAnalyticsEntity, Long> {

    @Query("SELECT a FROM CdsServiceAnalyticsEntity a WHERE a.serviceId = :serviceId AND a.periodEnd IS NULL")
    Optional<CdsServiceAnalyticsEntity> findCurrentPeriodByServiceId(String serviceId);

    /**
     * BUG-137 — tenant is derived by joining the parent service config, not from a column
     * here: cds_service_analytics.service_id is NOT NULL REFERENCES cds_service_config(id)
     * ON DELETE CASCADE (V7), so an analytics row's tenant is definitionally its service's.
     * Same reasoning as test_case (BUG-133) / measure_threshold (BUG-136).
     *
     * <p>The unscoped predecessor was reachable from GET /api/cds/services/analytics, which
     * has no admin check at all — any authenticated user saw every tenant's invocation and
     * error counts.
     */
    @Query("SELECT a FROM CdsServiceAnalyticsEntity a, CdsServiceConfigEntity c "
            + "WHERE a.serviceId = c.id AND c.tenantId = :tenantId AND a.periodEnd IS NULL")
    List<CdsServiceAnalyticsEntity> findAllCurrentPeriodByTenantId(@Param("tenantId") Long tenantId);
}
