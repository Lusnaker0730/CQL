package com.cqlplatform.repository;

import com.cqlplatform.entity.ConnectionHealthCheckEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConnectionHealthCheckRepository extends JpaRepository<ConnectionHealthCheckEntity, Long> {
    List<ConnectionHealthCheckEntity> findByConnectionIdOrderByCheckedAtDesc(Long connectionId);

    @Query("SELECT h FROM ConnectionHealthCheckEntity h WHERE h.connectionId = :connectionId AND h.checkedAt > :after ORDER BY h.checkedAt DESC")
    List<ConnectionHealthCheckEntity> findRecentByConnectionId(@Param("connectionId") Long connectionId, @Param("after") LocalDateTime after);

    Optional<ConnectionHealthCheckEntity> findFirstByConnectionIdOrderByCheckedAtDesc(Long connectionId);

    @Modifying
    @Query("DELETE FROM ConnectionHealthCheckEntity h WHERE h.checkedAt < :before")
    int deleteByCheckedAtBefore(@Param("before") LocalDateTime before);
}
