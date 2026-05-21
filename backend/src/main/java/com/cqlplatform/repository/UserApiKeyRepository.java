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

    /**
     * PAT-146 — {@code clearAutomatically=true} evicts any UserApiKey entities
     * loaded into the persistence context before this bulk UPDATE runs, so
     * subsequent reads in the same transaction don't see {@code active=true}
     * for keys that were just deactivated.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserApiKeyEntity k SET k.active = false WHERE k.username = :username AND k.active = true")
    int deactivateAllByUsername(String username);
}
