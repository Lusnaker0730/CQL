package com.cqlplatform.repository;

import com.cqlplatform.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
