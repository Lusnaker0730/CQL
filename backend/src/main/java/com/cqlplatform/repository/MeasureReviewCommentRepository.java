package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureReviewCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** PAT-262: review comment threads. Tenant-scoped like every other repository. */
@Repository
public interface MeasureReviewCommentRepository extends JpaRepository<MeasureReviewCommentEntity, Long> {

    List<MeasureReviewCommentEntity> findByMeasureIdAndTenantIdOrderByCreatedAtAsc(Long measureId, Long tenantId);

    Optional<MeasureReviewCommentEntity> findByIdAndMeasureIdAndTenantId(Long id, Long measureId, Long tenantId);

    /** Open threads = unresolved root comments. */
    long countByMeasureIdAndTenantIdAndParentIdIsNullAndResolvedFalse(Long measureId, Long tenantId);
}
