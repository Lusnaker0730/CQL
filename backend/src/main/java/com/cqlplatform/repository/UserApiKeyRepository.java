package com.cqlplatform.repository;

import com.cqlplatform.entity.UserApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApiKeyRepository extends JpaRepository<UserApiKeyEntity, Long> {

    Optional<UserApiKeyEntity> findByApiKeyAndActiveTrue(String apiKey);

    List<UserApiKeyEntity> findByUsername(String username);

    @Modifying
    @Query("UPDATE UserApiKeyEntity k SET k.active = false WHERE k.username = :username AND k.active = true")
    int deactivateAllByUsername(String username);
}
