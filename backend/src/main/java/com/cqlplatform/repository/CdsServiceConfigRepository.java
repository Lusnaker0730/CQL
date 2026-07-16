package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsServiceConfigRepository extends JpaRepository<CdsServiceConfigEntity, String> {

    /**
     * BUG-139 — the only by-id lookup: strictly the caller's own tenant. With shared collapsed
     * to within-tenant (Option A reversed), read and mutation lookups are the same — a service
     * in another tenant is simply not found, whether it is shared or not. (BUG-137 had a
     * separate READ lookup that allowed cross-tenant shared reads; that surface is gone.)
     */
    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems "
            + "WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<CdsServiceConfigEntity> findByIdAndTenantIdWithPrefetch(@Param("id") String id,
                                                                     @Param("tenantId") Long tenantId);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems WHERE c.enabled = true")
    List<CdsServiceConfigEntity> findAllEnabledWithPrefetch();

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems")
    List<CdsServiceConfigEntity> findAllWithPrefetch();

    // Tenant-scoped variants (Phase 2 — #698 PR-C2). The service NAME namespace stays
    // global (shared services are addressed by name on the anonymous surface); tenancy
    // applies to visibility and management, not naming.
    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems WHERE c.tenantId = :tenantId")
    List<CdsServiceConfigEntity> findAllByTenantIdWithPrefetch(Long tenantId);

    /**
     * BUG-139 — shared is now a WITHIN-TENANT concept (Option A reversed): a service the user
     * owns, or one a colleague in the SAME tenant published. The bare {@code OR c.shared = true}
     * used to cross tenants; the tenant predicate now wraps the whole OR so a shared service is
     * only visible to its own tenant, mirroring cql_library.accessLevel='public' (BUG-135).
     */
    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.tenantId = :tenantId AND (c.ownerUsername = :username OR c.shared = true)")
    List<CdsServiceConfigEntity> findByTenantIdAndOwnerUsernameOrSharedTrue(Long tenantId, String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.tenantId = :tenantId AND c.ownerUsername = :username AND c.enabled = true")
    List<CdsServiceConfigEntity> findByTenantIdAndOwnerUsernameAndEnabledTrue(Long tenantId, String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.ownerUsername = :username")
    List<CdsServiceConfigEntity> findByOwnerUsernameWithPrefetch(String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.ownerUsername = :username AND c.enabled = true")
    List<CdsServiceConfigEntity> findByOwnerUsernameAndEnabledTrue(String username);
    // BUG-139: findByOwnerUsernameOrSharedTrue and findBySharedTrueAndEnabledTrue removed —
    // both were the cross-tenant shared surface (Option A), now retired. Shared is within-tenant
    // via findByTenantIdAndOwnerUsernameOrSharedTrue.

    boolean existsById(String id);

    /**
     * BUG-137 — tenant-scoped. Version history exposes each version's full config (cqlContent
     * included), so it stays inside the owning tenant — the shared surface publishes a service
     * for invocation, not its edit history.
     */
    List<CdsServiceConfigEntity> findByTenantIdAndServiceNameOrderByVersionDesc(Long tenantId, String serviceName);

    @Query("SELECT MAX(c.version) FROM CdsServiceConfigEntity c WHERE c.serviceName = :serviceName")
    Optional<Integer> findMaxVersionByServiceName(String serviceName);
}
