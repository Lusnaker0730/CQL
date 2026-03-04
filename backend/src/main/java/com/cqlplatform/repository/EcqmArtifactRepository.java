package com.cqlplatform.repository;

import com.cqlplatform.entity.EcqmArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcqmArtifactRepository extends JpaRepository<EcqmArtifactEntity, Long> {

    List<EcqmArtifactEntity> findByOwnerUsername(String ownerUsername);

    boolean existsByNameAndVersionAndOwnerUsername(String name, String version, String ownerUsername);
}
