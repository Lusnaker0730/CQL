package com.cqlplatform.repository;

import com.cqlplatform.entity.FhirSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FhirSubscriptionRepository extends JpaRepository<FhirSubscriptionEntity, Long> {
    // Tenant-scoped variants (Phase 2 — #698): management reads must not cross clinics.
    List<FhirSubscriptionEntity> findByTenantIdAndConnectionIdOrderByCreatedAtDesc(Long tenantId, Long connectionId);
    List<FhirSubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    java.util.Optional<FhirSubscriptionEntity> findByIdAndTenantId(Long id, Long tenantId);
    /**
     * System lookup — intentionally unscoped: resolved from the anonymous webhook callback
     * (POST /api/ehr/subscriptions/callback, permitAll — external FHIR servers cannot
     * authenticate). The remote subscription id acts as the lookup token; the row's tenant
     * was bound at creation (inherited from its EHR connection).
     */
    Optional<FhirSubscriptionEntity> findByFhirSubscriptionId(String fhirSubscriptionId);
}
