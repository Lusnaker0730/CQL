package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportPopulationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasureReportPopulationRepository extends JpaRepository<MeasureReportPopulationEntity, Long> {

    List<MeasureReportPopulationEntity> findByMeasureReportGroupIdOrderByOrdinalAsc(Long measureReportGroupId);
}
