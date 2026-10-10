package com.cqlplatform.repository;

import com.cqlplatform.entity.ValueSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** PAT-230 — every method is tenant-scoped; there is deliberately no id-only or url-only finder. */
@Repository
public interface ValueSetRepository extends JpaRepository<ValueSetEntity, Long> {

    Optional<ValueSetEntity> findByIdAndTenantId(Long id, Long tenantId);

    Optional<ValueSetEntity> findByTenantIdAndUrlAndVersion(Long tenantId, String url, String version);

    List<ValueSetEntity> findByTenantIdAndUrlOrderByCreatedAtDescIdDesc(Long tenantId, String url);

    List<ValueSetEntity> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);

    boolean existsByTenantIdAndUrlAndVersion(Long tenantId, String url, String version);

    @Query("SELECT v FROM ValueSetEntity v WHERE v.tenantId = :tenantId AND ("
            + "LOWER(v.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(COALESCE(v.title, '')) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(v.url) LIKE LOWER(CONCAT('%', :term, '%'))) "
            + "ORDER BY v.updatedAt DESC")
    List<ValueSetEntity> searchByTenant(@Param("tenantId") Long tenantId, @Param("term") String term);
}
