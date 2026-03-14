package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeasureAuditRepository extends JpaRepository<MeasureAuditEntity, Long> {

    List<MeasureAuditEntity> findByMeasureIdOrderByCreatedAtDesc(Long measureId);
}
