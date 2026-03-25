package com.cqlplatform.repository;

import com.cqlplatform.entity.FhirSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FhirSubscriptionRepository extends JpaRepository<FhirSubscriptionEntity, Long> {
    List<FhirSubscriptionEntity> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<FhirSubscriptionEntity> findByStatusOrderByCreatedAtDesc(String status);
    List<FhirSubscriptionEntity> findAllByOrderByCreatedAtDesc();
    Optional<FhirSubscriptionEntity> findByFhirSubscriptionId(String fhirSubscriptionId);
}
