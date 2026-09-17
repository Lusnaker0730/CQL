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
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @Transactional(readOnly = true)
    public List<DepartmentEntity> getAll() {
        return repository.findByTenantIdAndActiveTrue(effectiveTenantId());
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentEntity> getByCode(String code) {
        return repository.findByTenantIdAndCode(effectiveTenantId(), code);
    }

    @Transactional(readOnly = true)
    public List<DepartmentEntity> getChildren(String parentCode) {
        return repository.findByTenantIdAndParentCode(effectiveTenantId(), parentCode);
    }

    @Transactional
    public DepartmentEntity create(DepartmentEntity entity) {
        if (repository.existsByTenantIdAndCode(effectiveTenantId(), entity.getCode())) {
            throw new IllegalArgumentException("Department code already exists: " + entity.getCode());
        }
        entity.setTenantId(effectiveTenantId());
        log.info("Created department: {}", entity.getCode());
        return repository.save(entity);
    }

    @Transactional
    public DepartmentEntity update(String code, DepartmentEntity updated) {
        DepartmentEntity existing = repository.findByTenantIdAndCode(effectiveTenantId(), code)
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
        DepartmentEntity existing = repository.findByTenantIdAndCode(effectiveTenantId(), code)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + code));
        existing.setActive(false);
        repository.save(existing);
        log.info("Deactivated department: {}", code);
    }
}
