package com.cqlplatform.controller;

import com.cqlplatform.entity.DepartmentEntity;
import com.cqlplatform.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping
    public ResponseEntity<List<DepartmentEntity>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{code}")
    public ResponseEntity<DepartmentEntity> getByCode(@PathVariable String code) {
        return service.getByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/children")
    public ResponseEntity<List<DepartmentEntity>> getChildren(@PathVariable String code) {
        return ResponseEntity.ok(service.getChildren(code));
    }

    @PostMapping
    public ResponseEntity<DepartmentEntity> create(@RequestBody DepartmentEntity entity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(entity));
    }

    @PutMapping("/{code}")
    public ResponseEntity<DepartmentEntity> update(
            @PathVariable String code,
            @RequestBody DepartmentEntity entity) {
        return ResponseEntity.ok(service.update(code, entity));
    }
}
