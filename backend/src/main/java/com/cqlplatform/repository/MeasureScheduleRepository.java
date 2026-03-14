package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeasureScheduleRepository extends JpaRepository<MeasureScheduleEntity, Long> {

    List<MeasureScheduleEntity> findByEnabledTrue();

    List<MeasureScheduleEntity> findByMeasureDefinitionId(Long measureDefinitionId);
}
