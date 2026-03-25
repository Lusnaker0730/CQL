package com.cqlplatform.repository;

import com.cqlplatform.entity.FailedImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedImportRepository extends JpaRepository<FailedImportEntity, Long> {
    List<FailedImportEntity> findByStatusOrderByCreatedAtDesc(String status);
    List<FailedImportEntity> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<FailedImportEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT f FROM FailedImportEntity f WHERE f.status = 'pending' AND f.nextRetryAt <= :now ORDER BY f.nextRetryAt")
    List<FailedImportEntity> findDueForRetry(@Param("now") LocalDateTime now);

    long countByStatus(String status);
}
