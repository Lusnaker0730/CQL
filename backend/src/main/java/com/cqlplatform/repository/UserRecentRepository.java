package com.cqlplatform.repository;

import com.cqlplatform.entity.UserRecentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRecentRepository extends JpaRepository<UserRecentEntity, Long> {
    List<UserRecentEntity> findByUsernameOrderByAccessedAtDesc(String username);
    Optional<UserRecentEntity> findByUsernameAndLibraryId(String username, String libraryId);
    void deleteByUsernameAndLibraryId(String username, String libraryId);
    long countByUsername(String username);
    void deleteAllByUsername(String username);
}
