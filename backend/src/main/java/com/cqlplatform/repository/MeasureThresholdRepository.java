package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureThresholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeasureThresholdRepository extends JpaRepository<MeasureThresholdEntity, Long> {

    List<MeasureThresholdEntity> findByMeasureDefinitionIdAndActiveTrue(Long measureDefinitionId);

    List<MeasureThresholdEntity> findByActiveTrue();

    List<MeasureThresholdEntity> findByDepartmentAndActiveTrue(String department);
}
