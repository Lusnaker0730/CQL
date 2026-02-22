package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsServiceConfigRepository extends JpaRepository<CdsServiceConfigEntity, String> {

    List<CdsServiceConfigEntity> findByEnabledTrue();

    List<CdsServiceConfigEntity> findByHook(String hook);

    List<CdsServiceConfigEntity> findByHookAndEnabledTrue(String hook);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems WHERE c.id = :id")
    Optional<CdsServiceConfigEntity> findByIdWithPrefetch(String id);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems WHERE c.enabled = true")
    List<CdsServiceConfigEntity> findAllEnabledWithPrefetch();

    List<CdsServiceConfigEntity> findByOwnerUsername(String username);

    @Query("SELECT DISTINCT c FROM CdsServiceConfigEntity c LEFT JOIN FETCH c.prefetchItems")
    List<CdsServiceConfigEntity> findAllWithPrefetch();

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

    @Query("SELECT c FROM CdsServiceConfigEntity c WHERE c.serviceName = :serviceName AND c.enabled = true " +
            "AND c.version = (SELECT MAX(c2.version) FROM CdsServiceConfigEntity c2 WHERE c2.serviceName = :serviceName AND c2.enabled = true)")
    Optional<CdsServiceConfigEntity> findLatestEnabledByServiceName(String serviceName);

    @Query("SELECT MAX(c.version) FROM CdsServiceConfigEntity c WHERE c.serviceName = :serviceName")
    Optional<Integer> findMaxVersionByServiceName(String serviceName);
}
