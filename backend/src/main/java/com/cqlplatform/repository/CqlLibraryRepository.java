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
}
