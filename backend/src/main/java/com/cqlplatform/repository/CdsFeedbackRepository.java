package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CdsFeedbackRepository extends JpaRepository<CdsFeedbackEntity, Long> {

    List<CdsFeedbackEntity> findByServiceIdOrderByCreatedAtDesc(String serviceId);

    long countByServiceIdAndOutcome(String serviceId, String outcome);
}
