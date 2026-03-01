package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CdsArtifactRepository extends JpaRepository<CdsArtifactEntity, Long> {

    List<CdsArtifactEntity> findByOwnerUsername(String ownerUsername);

    boolean existsByNameAndVersionAndOwnerUsername(String name, String version, String ownerUsername);
}
