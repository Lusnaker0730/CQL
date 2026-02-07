package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MeasureReportRepository extends JpaRepository<MeasureReportEntity, Long> {

    List<MeasureReportEntity> findByMeasureDefinitionIdOrderByCreatedAtDesc(Long measureDefinitionId);

    List<MeasureReportEntity> findByMeasureNameOrderByCreatedAtDesc(String measureName);

    List<MeasureReportEntity> findByMeasureNameOrderByPeriodStartAsc(String measureName);

    List<MeasureReportEntity> findByMeasureNameAndPeriodStartAndPeriodEnd(
            String measureName, LocalDate periodStart, LocalDate periodEnd);

    List<MeasureReportEntity> findTop50ByOrderByCreatedAtDesc();
}
