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

    List<MeasureDefinitionEntity> findByOwnerUsername(String ownerUsername);

    @org.springframework.data.jpa.repository.Query(
        "SELECT e FROM MeasureDefinitionEntity e WHERE e.accessLevel = 'public' " +
        "OR e.sharedWith LIKE :pattern")
    List<MeasureDefinitionEntity> findSharedWithUser(
        @org.springframework.data.repository.query.Param("pattern") String pattern);

    // Phase 2 — tenant-scoped MANAGEMENT queries.
    Optional<MeasureDefinitionEntity> findByIdAndTenantId(Long id, Long tenantId);

    Optional<MeasureDefinitionEntity> findByTenantIdAndNameAndVersion(Long tenantId, String name, String version);

    List<MeasureDefinitionEntity> findByTenantId(Long tenantId);

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
