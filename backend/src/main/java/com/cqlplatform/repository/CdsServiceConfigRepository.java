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

    boolean existsById(String id);
}
