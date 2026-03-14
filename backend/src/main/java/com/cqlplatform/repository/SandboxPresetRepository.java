package com.cqlplatform.repository;

import com.cqlplatform.entity.SandboxPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SandboxPresetRepository extends JpaRepository<SandboxPresetEntity, Long> {

    List<SandboxPresetEntity> findByOwnerUsernameOrderByUpdatedAtDesc(String ownerUsername);

    @Query("SELECT p FROM SandboxPresetEntity p WHERE p.ownerUsername = :username OR p.shared = true ORDER BY p.updatedAt DESC")
    List<SandboxPresetEntity> findAccessible(@Param("username") String username);
}
