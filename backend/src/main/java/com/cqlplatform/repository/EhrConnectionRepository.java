package com.cqlplatform.repository;

import com.cqlplatform.entity.EhrConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EhrConnectionRepository extends JpaRepository<EhrConnectionEntity, Long> {
    List<EhrConnectionEntity> findByActiveTrue();
    List<EhrConnectionEntity> findByDepartmentAndActiveTrue(String department);
    List<EhrConnectionEntity> findByStatusAndActiveTrue(String status);
}
