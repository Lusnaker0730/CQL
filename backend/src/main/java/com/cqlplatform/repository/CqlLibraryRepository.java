package com.cqlplatform.repository;

import com.cqlplatform.entity.CqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CqlLibraryRepository extends JpaRepository<CqlLibraryEntity, Long> {

    Optional<CqlLibraryEntity> findByNameAndVersion(String name, String version);

    List<CqlLibraryEntity> findByName(String name);

    List<CqlLibraryEntity> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);

    boolean existsByNameAndVersion(String name, String version);

    List<CqlLibraryEntity> findByOwnerUsername(String ownerUsername);

    List<CqlLibraryEntity> findByDependenciesContaining(String libraryName);

    // Phase 2 — tenant-scoped MANAGEMENT queries (request thread). The execution-path DB
    // library provider is deliberately NOT scoped here yet (that needs async tenant
    // propagation) — see the enforcement follow-up.
    List<CqlLibraryEntity> findByTenantId(Long tenantId);

    Optional<CqlLibraryEntity> findByTenantIdAndNameAndVersion(Long tenantId, String name, String version);

    List<CqlLibraryEntity> findByTenantIdAndName(Long tenantId, String name);

    boolean existsByTenantIdAndNameAndVersion(Long tenantId, String name, String version);

    @org.springframework.data.jpa.repository.Query(
        "SELECT e FROM CqlLibraryEntity e WHERE e.tenantId = :tenantId AND " +
        "(LOWER(e.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
        "OR LOWER(e.description) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<CqlLibraryEntity> searchByTenant(
        @org.springframework.data.repository.query.Param("tenantId") Long tenantId,
        @org.springframework.data.repository.query.Param("term") String term);

    @org.springframework.data.jpa.repository.Query(
        "SELECT e FROM CqlLibraryEntity e WHERE e.accessLevel = 'public' " +
        "OR e.sharedWith LIKE :pattern")
    List<CqlLibraryEntity> findSharedWithUser(
        @org.springframework.data.repository.query.Param("pattern") String pattern);

    /**
     * Lightweight projection for the metadata endpoint. Selects ONLY the columns
     * {@link com.cqlplatform.model.LibraryMetadataDTO} reads (name, version, elmJson),
     * so the query skips the heavy {@code cql_content} TEXT column that the metadata
     * endpoint never uses — instead of loading full entities via findAll().
     */
    interface LibraryMetadataView {
        String getName();
        String getVersion();
        String getElmJson();
    }

    @org.springframework.data.jpa.repository.Query(
        "SELECT e.name AS name, e.version AS version, e.elmJson AS elmJson FROM CqlLibraryEntity e")
    List<LibraryMetadataView> findAllMetadata();

    @org.springframework.data.jpa.repository.Query(
        "SELECT e.name AS name, e.version AS version, e.elmJson AS elmJson " +
        "FROM CqlLibraryEntity e WHERE e.tenantId = :tenantId")
    List<LibraryMetadataView> findMetadataByTenantId(
        @org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
