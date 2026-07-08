package com.cqlplatform.repository;

import com.cqlplatform.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByCode(String code);

    List<TenantEntity> findByActiveTrue();

    boolean existsByCode(String code);
}
