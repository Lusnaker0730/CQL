package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReviewerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** PAT-262: assigned reviewers. Tenant-scoped like every other repository. */
@Repository
public interface MeasureReviewerRepository extends JpaRepository<MeasureReviewerEntity, Long> {

    List<MeasureReviewerEntity> findByMeasureIdAndTenantIdOrderByAssignedAtAsc(Long measureId, Long tenantId);

    Optional<MeasureReviewerEntity> findByMeasureIdAndTenantIdAndUsername(Long measureId, Long tenantId, String username);

    boolean existsByMeasureIdAndTenantIdAndUsername(Long measureId, Long tenantId, String username);

    /** A new review round: every reviewer is back to {@code pending}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MeasureReviewerEntity r set r.decision = 'pending', r.decisionComment = null, r.decidedAt = null"
            + " where r.measureId = :measureId and r.tenantId = :tenantId")
    int resetDecisions(@Param("measureId") Long measureId, @Param("tenantId") Long tenantId);
}
