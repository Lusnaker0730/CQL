package com.cqlplatform.controller;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import com.cqlplatform.service.measure.IndicatorCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
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
    public ResponseEntity<IndicatorCatalogEntity> create(@RequestBody IndicatorCatalogEntity entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(entity));
    }

    @PutMapping("/{code}")
    public ResponseEntity<IndicatorCatalogEntity> update(
            @PathVariable String code,
            @RequestParam(defaultValue = "MOH") String source,
            @RequestBody IndicatorCatalogEntity entity) {
        return ResponseEntity.ok(service.update(code, source, entity));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> bulkImport(@RequestBody List<Map<String, Object>> entries) {
        return ResponseEntity.ok(service.bulkImport(entries));
    }
}
