package com.cqlplatform.repository;

import com.cqlplatform.entity.CqlLibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CqlLibraryRepository extends JpaRepository<CqlLibraryEntity, Long> {

    Optional<CqlLibraryEntity> findByNameAndVersion(String name, String version);

    List<CqlLibraryEntity> findByName(String name);

    List<CqlLibraryEntity> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);

    boolean existsByNameAndVersion(String name, String version);
}
