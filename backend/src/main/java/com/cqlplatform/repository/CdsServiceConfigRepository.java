package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsServiceConfigRepository extends JpaRepository<CdsServiceConfigEntity, String> {

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems WHERE c.id = :id")
    Optional<CdsServiceConfigEntity> findByIdWithPrefetch(String id);

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

    List<CdsServiceConfigEntity> findByServiceNameOrderByVersionDesc(String serviceName);

    @Query("SELECT MAX(c.version) FROM CdsServiceConfigEntity c WHERE c.serviceName = :serviceName")
    Optional<Integer> findMaxVersionByServiceName(String serviceName);
}
