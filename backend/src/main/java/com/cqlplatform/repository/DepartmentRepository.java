package com.cqlplatform.repository;

import com.cqlplatform.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {

    Optional<DepartmentEntity> findByCode(String code);

    List<DepartmentEntity> findByActiveTrue();

    List<DepartmentEntity> findByParentCode(String parentCode);

    boolean existsByCode(String code);
}
