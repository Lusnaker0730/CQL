package com.cqlplatform.repository;

import com.cqlplatform.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);

    // Tenant management (PAT-201 / #699)
    java.util.List<UserEntity> findByTenantId(Long tenantId);
    java.util.List<UserEntity> findByTenantIdIsNull();
    Optional<UserEntity> findByEmailHash(String emailHash);
    Optional<UserEntity> findByAuthProviderAndExternalId(UserEntity.AuthProvider authProvider, String externalId);

    @Query("SELECT u.tokenVersion FROM UserEntity u WHERE u.username = :username")
    Optional<Integer> findTokenVersionByUsername(@Param("username") String username);

    @Modifying
    @Query("UPDATE UserEntity u SET u.tokenVersion = u.tokenVersion + 1, u.updatedAt = CURRENT_TIMESTAMP WHERE u.username = :username")
    int incrementTokenVersion(@Param("username") String username);
}
