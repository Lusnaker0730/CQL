package com.cqlplatform.repository;

import com.cqlplatform.entity.TestCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCaseEntity, Long> {

    List<TestCaseEntity> findByMeasureDefinitionIdOrderByCreatedAtAsc(Long measureDefinitionId);

    long countByMeasureDefinitionId(Long measureDefinitionId);

    void deleteByMeasureDefinitionId(Long measureDefinitionId);

    /**
     * PAT-253: stores a validation outcome without touching any other column. The background
     * validation runs on the executor outside a transaction, so the entity it loaded before the
     * (slow) HAPI run is detached — saving it would merge that stale snapshot over whatever changed
     * meanwhile (an edit lock taken, a title edited). The CI smoke run caught the lock being wiped
     * that way. The persistence context is cleared afterwards so a caller inside a transaction
     * re-reads the row instead of its stale copy.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update TestCaseEntity t set t.validationStatus = :status, t.validationSummary = :summary, "
            + "t.validatedAt = :validatedAt where t.id = :id")
    int storeValidation(@Param("id") Long id, @Param("status") String status, @Param("summary") String summary,
                        @Param("validatedAt") LocalDateTime validatedAt);
}
