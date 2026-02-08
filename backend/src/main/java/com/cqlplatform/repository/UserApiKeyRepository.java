package com.cqlplatform.repository;

import com.cqlplatform.entity.UserApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApiKeyRepository extends JpaRepository<UserApiKeyEntity, Long> {

    Optional<UserApiKeyEntity> findByApiKeyAndActiveTrue(String apiKey);

    List<UserApiKeyEntity> findByUsername(String username);
}
