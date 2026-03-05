package com.cqlplatform.repository;

import com.cqlplatform.entity.EcqmExternalCqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcqmExternalCqlLibraryRepository extends JpaRepository<EcqmExternalCqlLibraryEntity, Long> {

    List<EcqmExternalCqlLibraryEntity> findByArtifactId(Long artifactId);

    Optional<EcqmExternalCqlLibraryEntity> findByIdAndArtifactId(Long id, Long artifactId);

    void deleteByArtifactId(Long artifactId);
}
