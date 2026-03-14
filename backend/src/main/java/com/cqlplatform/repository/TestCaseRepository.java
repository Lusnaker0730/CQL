package com.cqlplatform.repository;

import com.cqlplatform.entity.TestCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCaseEntity, Long> {

    List<TestCaseEntity> findByMeasureDefinitionIdOrderByCreatedAtAsc(Long measureDefinitionId);

    long countByMeasureDefinitionId(Long measureDefinitionId);

    void deleteByMeasureDefinitionId(Long measureDefinitionId);
}
