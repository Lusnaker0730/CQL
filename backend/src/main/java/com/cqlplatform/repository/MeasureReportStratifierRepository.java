package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportStratifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasureReportStratifierRepository extends JpaRepository<MeasureReportStratifierEntity, Long> {

    List<MeasureReportStratifierEntity> findByMeasureReportGroupIdOrderByOrdinalAsc(Long measureReportGroupId);
}
