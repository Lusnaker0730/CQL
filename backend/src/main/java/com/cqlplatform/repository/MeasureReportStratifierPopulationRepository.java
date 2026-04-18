package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportStratifierPopulationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasureReportStratifierPopulationRepository
        extends JpaRepository<MeasureReportStratifierPopulationEntity, Long> {

    List<MeasureReportStratifierPopulationEntity> findByMeasureReportStratifierIdOrderByOrdinalAsc(
            Long measureReportStratifierId);
}
