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

    List<MeasureDefinitionEntity> findByStatus(String status);

    List<MeasureDefinitionEntity> findByNameContainingIgnoreCaseOrTitleContainingIgnoreCase(String name, String title);

    boolean existsByNameAndVersion(String name, String version);

    List<MeasureDefinitionEntity> findByOwnerUsername(String ownerUsername);

    List<MeasureDefinitionEntity> findByAccessLevel(String accessLevel);
}
