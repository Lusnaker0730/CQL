package com.cqlplatform.service.measure;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import com.cqlplatform.repository.IndicatorCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndicatorCatalogService {

    private final IndicatorCatalogRepository repository;

    @Transactional(readOnly = true)
    public List<IndicatorCatalogEntity> search(String source, String category, String searchTerm) {
        if (searchTerm != null && !searchTerm.isBlank()) {
            return repository.findByNameContainingIgnoreCaseOrNameEnContainingIgnoreCaseOrCodeContainingIgnoreCase(
                    searchTerm, searchTerm, searchTerm);
        }
        if (source != null && category != null) {
            return repository.findBySourceAndCategory(source, category);
        }
        if (source != null) {
            return repository.findBySource(source);
        }
        if (category != null) {
            return repository.findByCategory(category);
        }
        return repository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<IndicatorCatalogEntity> getByCodeAndSource(String code, String source) {
        return repository.findByCodeAndSource(code, source);
    }

    @Transactional
    public IndicatorCatalogEntity create(IndicatorCatalogEntity entity) {
        if (repository.existsByCodeAndSource(entity.getCode(), entity.getSource())) {
            throw new IllegalArgumentException(
                    "Indicator already exists: " + entity.getCode() + " (" + entity.getSource() + ")");
        }
        return repository.save(entity);
    }

    @Transactional
    public IndicatorCatalogEntity update(String code, String source, IndicatorCatalogEntity updated) {
        IndicatorCatalogEntity existing = repository.findByCodeAndSource(code, source)
                .orElseThrow(() -> new IllegalArgumentException("Indicator not found: " + code));

        existing.setName(updated.getName());
        existing.setNameEn(updated.getNameEn());
        existing.setCategory(updated.getCategory());
        existing.setSubcategory(updated.getSubcategory());
        existing.setDescription(updated.getDescription());
        existing.setVersion(updated.getVersion());
        existing.setActive(updated.getActive());

        return repository.save(existing);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> bulkImport(List<Map<String, Object>> entries) {
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> entry : entries) {
            try {
                String code = (String) entry.get("code");
                String source = (String) entry.get("source");
                if (code == null || source == null) {
                    errors.add("Missing code or source in entry");
                    continue;
                }
                if (repository.existsByCodeAndSource(code, source)) {
                    skipped++;
                    continue;
                }

                IndicatorCatalogEntity entity = IndicatorCatalogEntity.builder()
                        .code(code)
                        .name((String) entry.get("name"))
                        .nameEn((String) entry.get("nameEn"))
                        .category((String) entry.get("category"))
                        .subcategory((String) entry.get("subcategory"))
                        .description((String) entry.get("description"))
                        .source(source)
                        .version((String) entry.get("version"))
                        .active(entry.get("active") != null ? (Boolean) entry.get("active") : true)
                        .build();
                repository.save(entity);
                created++;
            } catch (Exception e) {
                errors.add("Error importing entry: " + e.getMessage());
            }
        }

        log.info("Bulk import: {} created, {} skipped, {} errors", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }
}
