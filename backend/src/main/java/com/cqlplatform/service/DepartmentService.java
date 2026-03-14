package com.cqlplatform.service;

import com.cqlplatform.entity.DepartmentEntity;
import com.cqlplatform.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepository repository;

    @Transactional(readOnly = true)
    public List<DepartmentEntity> getAll() {
        return repository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentEntity> getByCode(String code) {
        return repository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public List<DepartmentEntity> getChildren(String parentCode) {
        return repository.findByParentCode(parentCode);
    }

    @Transactional
    public DepartmentEntity create(DepartmentEntity entity) {
        if (repository.existsByCode(entity.getCode())) {
            throw new IllegalArgumentException("Department code already exists: " + entity.getCode());
        }
        log.info("Created department: {}", entity.getCode());
        return repository.save(entity);
    }

    @Transactional
    public DepartmentEntity update(String code, DepartmentEntity updated) {
        DepartmentEntity existing = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + code));

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setParentCode(updated.getParentCode());
        existing.setActive(updated.getActive());

        log.info("Updated department: {}", code);
        return repository.save(existing);
    }

    @Transactional
    public void delete(String code) {
        DepartmentEntity existing = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + code));
        existing.setActive(false);
        repository.save(existing);
        log.info("Deactivated department: {}", code);
    }
}
