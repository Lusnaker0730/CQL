package com.cqlplatform.repository;

import com.cqlplatform.entity.UserRecentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying
    @Query("DELETE FROM UserRecentEntity r WHERE r.username = :username AND r.id NOT IN " +
            "(SELECT r2.id FROM UserRecentEntity r2 WHERE r2.username = :username ORDER BY r2.accessedAt DESC LIMIT 10)")
    void deleteOldestBeyondLimit(@Param("username") String username);
}
