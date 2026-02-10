package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsExternalCqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CdsExternalCqlLibraryRepository extends JpaRepository<CdsExternalCqlLibraryEntity, Long> {

    List<CdsExternalCqlLibraryEntity> findByArtifactId(Long artifactId);

    void deleteByArtifactId(Long artifactId);
}
