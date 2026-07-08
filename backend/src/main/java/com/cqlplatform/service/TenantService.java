package com.cqlplatform.service;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tenant (clinic) management — the platform super-admin provisions a tenant per clinic.
 * Foundation for Phase 2 multi-tenancy; row-level enforcement is added in follow-up work.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantEntity createTenant(String code, String name) {
        if (tenantRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Tenant with code already exists: " + code);
        }
        return tenantRepository.save(
                TenantEntity.builder().code(code).name(name).active(true).build());
    }

    @Transactional(readOnly = true)
    public List<TenantEntity> listActive() {
        return tenantRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public TenantEntity getByCode(String code) {
        return tenantRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + code));
    }

    @Transactional(readOnly = true)
    public TenantEntity getById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }
}
