package com.cqlplatform.repository;

import com.cqlplatform.entity.BatchImportJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchImportJobRepository extends JpaRepository<BatchImportJobEntity, Long> {
    List<BatchImportJobEntity> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    List<BatchImportJobEntity> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<BatchImportJobEntity> findByStatusOrderByCreatedAtDesc(String status);
    List<BatchImportJobEntity> findAllByOrderByCreatedAtDesc();
}
