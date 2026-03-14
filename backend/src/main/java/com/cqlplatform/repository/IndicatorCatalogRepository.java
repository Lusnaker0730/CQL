package com.cqlplatform.repository;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorCatalogRepository extends JpaRepository<IndicatorCatalogEntity, Long> {

    Optional<IndicatorCatalogEntity> findByCodeAndSource(String code, String source);

    List<IndicatorCatalogEntity> findBySource(String source);

    List<IndicatorCatalogEntity> findByCategory(String category);

    List<IndicatorCatalogEntity> findBySourceAndCategory(String source, String category);

    List<IndicatorCatalogEntity> findByActiveTrue();

    List<IndicatorCatalogEntity> findByNameContainingIgnoreCaseOrNameEnContainingIgnoreCaseOrCodeContainingIgnoreCase(
            String name, String nameEn, String code);

    boolean existsByCodeAndSource(String code, String source);
}
