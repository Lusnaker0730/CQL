package com.cqlplatform.controller;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import com.cqlplatform.service.measure.IndicatorCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
@Validated
public class IndicatorCatalogController {

    private final IndicatorCatalogService service;

    @GetMapping
    public ResponseEntity<List<IndicatorCatalogEntity>> search(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.search(source, category, search));
    }

    @GetMapping("/{code}")
    public ResponseEntity<IndicatorCatalogEntity> getByCode(
            @PathVariable String code,
            @RequestParam(defaultValue = "MOH") String source) {
        return service.getByCodeAndSource(code, source)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IndicatorCatalogEntity> create(@Valid @RequestBody IndicatorCatalogEntity entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(entity));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IndicatorCatalogEntity> update(
            @PathVariable String code,
            @RequestParam(defaultValue = "MOH") String source,
            @Valid @RequestBody IndicatorCatalogEntity entity) {
        return ResponseEntity.ok(service.update(code, source, entity));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String code,
            @RequestParam(defaultValue = "MOH") String source) {
        service.delete(code, source);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkImport(
            @Size(max = 500, message = "Bulk import is limited to 500 entries per request")
            @RequestBody List<Map<String, Object>> entries) {
        return ResponseEntity.ok(service.bulkImport(entries));
    }
}
