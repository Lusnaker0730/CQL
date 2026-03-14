package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsExternalCqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CdsExternalCqlLibraryRepository extends JpaRepository<CdsExternalCqlLibraryEntity, Long> {

    List<CdsExternalCqlLibraryEntity> findByArtifactId(Long artifactId);

    Optional<CdsExternalCqlLibraryEntity> findByIdAndArtifactId(Long id, Long artifactId);

    void deleteByArtifactId(Long artifactId);

    @Query("SELECT DISTINCT e.fhirVersion FROM CdsExternalCqlLibraryEntity e WHERE e.artifactId = :artifactId AND e.fhirVersion IS NOT NULL")
    Set<String> findDistinctFhirVersionsByArtifactId(Long artifactId);
}
