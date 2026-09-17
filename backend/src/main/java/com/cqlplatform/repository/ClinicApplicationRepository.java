package com.cqlplatform.repository;

import com.cqlplatform.entity.ClinicApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicApplicationRepository extends JpaRepository<ClinicApplicationEntity, Long> {

    List<ClinicApplicationEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<ClinicApplicationEntity> findAllByOrderByCreatedAtDesc();
}
