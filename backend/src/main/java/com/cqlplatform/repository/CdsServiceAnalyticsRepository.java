package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsServiceAnalyticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CdsServiceAnalyticsRepository extends JpaRepository<CdsServiceAnalyticsEntity, Long> {

    @Query("SELECT a FROM CdsServiceAnalyticsEntity a WHERE a.serviceId = :serviceId AND a.periodEnd IS NULL")
    Optional<CdsServiceAnalyticsEntity> findCurrentPeriodByServiceId(String serviceId);

    @Query("SELECT a FROM CdsServiceAnalyticsEntity a WHERE a.periodEnd IS NULL")
    List<CdsServiceAnalyticsEntity> findAllCurrentPeriod();
}
