package com.cqlplatform.repository;

import com.cqlplatform.entity.UserFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavoriteEntity, Long> {
    List<UserFavoriteEntity> findByUsernameOrderByCreatedAtDesc(String username);
    Optional<UserFavoriteEntity> findByUsernameAndLibraryId(String username, String libraryId);
    void deleteByUsernameAndLibraryId(String username, String libraryId);
    boolean existsByUsernameAndLibraryId(String username, String libraryId);
}
