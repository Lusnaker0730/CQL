package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeasureDefinitionRepository extends JpaRepository<MeasureDefinitionEntity, Long> {

    Optional<MeasureDefinitionEntity> findByNameAndVersion(String name, String version);

    List<MeasureDefinitionEntity> findByName(String name);

    List<MeasureDefinitionEntity> findByNameContainingIgnoreCaseOrTitleContainingIgnoreCase(String name, String title);

    List<MeasureDefinitionEntity> findByDepartment(String department);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m FROM MeasureDefinitionEntity m WHERE m.department = :department " +
        "AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<MeasureDefinitionEntity> findByDepartmentAndSearchTerm(
        @org.springframework.data.repository.query.Param("department") String department,
        @org.springframework.data.repository.query.Param("search") String search);

    boolean existsByNameAndVersion(String name, String version);

    /**
     * BUG-135 — tenant-scoped. The unscoped predecessors returned every tenant's rows:
     * sharing ('public' / sharedWith) is a within-tenant concept, and the /owner and /shared
     * endpoints let a ROLE_ADMIN pass an arbitrary username, so without the tenant predicate
     * a clinic ADMIN read another tenant's measures.
     */
    List<MeasureDefinitionEntity> findByTenantIdAndOwnerUsername(Long tenantId, String ownerUsername);

    @org.springframework.data.jpa.repository.Query(
        "SELECT e FROM MeasureDefinitionEntity e WHERE e.tenantId = :tenantId " +
        "AND (e.accessLevel = 'public' OR e.sharedWith LIKE :pattern)")
    List<MeasureDefinitionEntity> findSharedWithUser(
        @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
        @org.springframework.data.repository.query.Param("pattern") String pattern);

    // Phase 2 — tenant-scoped MANAGEMENT queries.
    Optional<MeasureDefinitionEntity> findByIdAndTenantId(Long id, Long tenantId);

    Optional<MeasureDefinitionEntity> findByTenantIdAndNameAndVersion(Long tenantId, String name, String version);

    List<MeasureDefinitionEntity> findByTenantId(Long tenantId);

    /**
     * BUG-136 — tenant-scoped bulk lookup by id. Its caller (DashboardService trend labels)
     * derives the ids from already-scoped reports, so an unscoped findAllById would be safe
     * today; scoping it here means that safety no longer depends on reasoning about the
     * caller, and survives someone changing where the ids come from.
     */
    List<MeasureDefinitionEntity> findByTenantIdAndIdIn(Long tenantId, java.util.Collection<Long> ids);

    List<MeasureDefinitionEntity> findByTenantIdAndName(Long tenantId, String name);

    List<MeasureDefinitionEntity> findByTenantIdAndDepartment(Long tenantId, String department);

    boolean existsByTenantIdAndNameAndVersion(Long tenantId, String name, String version);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m FROM MeasureDefinitionEntity m WHERE m.tenantId = :tenantId AND " +
        "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<MeasureDefinitionEntity> searchByTenant(
        @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
        @org.springframework.data.repository.query.Param("search") String search);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m FROM MeasureDefinitionEntity m WHERE m.tenantId = :tenantId " +
        "AND m.department = :department AND " +
        "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<MeasureDefinitionEntity> findByTenantIdAndDepartmentAndSearchTerm(
        @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
        @org.springframework.data.repository.query.Param("department") String department,
        @org.springframework.data.repository.query.Param("search") String search);
}
