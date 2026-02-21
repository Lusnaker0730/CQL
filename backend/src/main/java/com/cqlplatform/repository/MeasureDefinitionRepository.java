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

    List<MeasureDefinitionEntity> findByDepartment(String department);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m FROM MeasureDefinitionEntity m WHERE m.department = :department " +
        "AND (LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
        "OR LOWER(m.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<MeasureDefinitionEntity> findByDepartmentAndSearchTerm(
        @org.springframework.data.repository.query.Param("department") String department,
        @org.springframework.data.repository.query.Param("search") String search);

    boolean existsByNameAndVersion(String name, String version);

    List<MeasureDefinitionEntity> findByOwnerUsername(String ownerUsername);

    List<MeasureDefinitionEntity> findByAccessLevel(String accessLevel);
}
