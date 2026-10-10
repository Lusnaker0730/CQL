package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureSetEntity;
import com.cqlplatform.repository.MeasureSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAT-253 — the measure set a measure's versions share. Opened by {@code create} (and by the
 * eCQM builder's first publish, and the demo seed), carried over by {@code createVersionAs},
 * renamed when a version is renamed. Lineage queries themselves live with the measures
 * ({@code MeasureDefinitionRepository.findByMeasureSetId}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureSetService {

    private final MeasureSetRepository repository;

    /** Opens a new set in {@code tenantId}; returns its id. */
    @Transactional
    public Long createFor(Long tenantId, String name) {
        MeasureSetEntity set = repository.save(MeasureSetEntity.builder().tenantId(tenantId).name(name).build());
        log.debug("Opened measure set {} '{}' in tenant {}", set.getId(), name, tenantId);
        return set.getId();
    }

    /**
     * Follows a rename: the set's name is display-only, so an unknown / cross-tenant id (a row
     * built before V79 in a test, or a stale reference) is ignored rather than failing the save.
     */
    @Transactional
    public void rename(Long setId, Long tenantId, String name) {
        if (setId == null || name == null) return;
        repository.findByIdAndTenantId(setId, tenantId).ifPresent(set -> {
            if (!name.equals(set.getName())) {
                set.setName(name);
                repository.save(set);
            }
        });
    }
}
