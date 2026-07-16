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
     * BUG-137 — READ lookup: the caller's own tenant, plus any tenant's shared service.
     * The shared surface is deliberately tenant-agnostic (Option A, #698) — a published
     * service is visible to everyone, mirroring findByTenantIdAndOwnerUsernameOrSharedTrue
     * so the list and the detail view agree. Do NOT use this for mutations: see
     * findByIdAndTenantIdWithPrefetch.
     */
    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems "
            + "WHERE c.id = :id AND (c.tenantId = :tenantId OR c.shared = true)")
    Optional<CdsServiceConfigEntity> findReadableByIdWithPrefetch(@Param("id") String id,
                                                                  @Param("tenantId") Long tenantId);

    /**
     * BUG-137 — MUTATION lookup: strictly the caller's own tenant, never the shared surface.
     * Being able to see a published service must not imply being able to change it: without
     * this, a clinic ADMIN could unshare (or publish) another tenant's service.
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

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE (c.tenantId = :tenantId AND c.ownerUsername = :username) OR c.shared = true")
    List<CdsServiceConfigEntity> findByTenantIdAndOwnerUsernameOrSharedTrue(Long tenantId, String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.tenantId = :tenantId AND c.ownerUsername = :username AND c.enabled = true")
    List<CdsServiceConfigEntity> findByTenantIdAndOwnerUsernameAndEnabledTrue(Long tenantId, String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.ownerUsername = :username")
    List<CdsServiceConfigEntity> findByOwnerUsernameWithPrefetch(String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.ownerUsername = :username OR c.shared = true")
    List<CdsServiceConfigEntity> findByOwnerUsernameOrSharedTrue(String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.ownerUsername = :username AND c.enabled = true")
    List<CdsServiceConfigEntity> findByOwnerUsernameAndEnabledTrue(String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems " +
            "WHERE c.shared = true AND c.enabled = true")
    List<CdsServiceConfigEntity> findBySharedTrueAndEnabledTrue();

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
