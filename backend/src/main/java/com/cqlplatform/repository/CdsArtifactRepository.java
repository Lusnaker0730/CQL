package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CdsArtifactRepository extends JpaRepository<CdsArtifactEntity, Long> {

    List<CdsArtifactEntity> findByOwnerUsername(String ownerUsername);

    List<CdsArtifactEntity> findByStatus(String status);

    List<CdsArtifactEntity> findByOwnerUsernameAndStatus(String ownerUsername, String status);

    List<CdsArtifactEntity> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndVersionAndOwnerUsername(String name, String version, String ownerUsername);
}
