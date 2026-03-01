package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsExternalCqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsExternalCqlLibraryRepository extends JpaRepository<CdsExternalCqlLibraryEntity, Long> {

    List<CdsExternalCqlLibraryEntity> findByArtifactId(Long artifactId);

    Optional<CdsExternalCqlLibraryEntity> findByIdAndArtifactId(Long id, Long artifactId);

    void deleteByArtifactId(Long artifactId);
}
