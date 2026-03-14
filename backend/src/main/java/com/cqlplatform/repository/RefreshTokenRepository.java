package com.cqlplatform.repository;

import com.cqlplatform.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.familyId = :familyId")
    int revokeByFamilyId(@Param("familyId") String familyId);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId")
    int revokeByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.revoked = true OR r.expiresAt < :cutoff")
    int deleteExpiredOrRevoked(@Param("cutoff") LocalDateTime cutoff);
}
