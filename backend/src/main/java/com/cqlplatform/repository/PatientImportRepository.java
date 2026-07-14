package com.cqlplatform.repository;

import com.cqlplatform.entity.PatientImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatientImportRepository extends JpaRepository<PatientImportEntity, Long> {
    List<PatientImportEntity> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<PatientImportEntity> findByImportedByOrderByCreatedAtDesc(String importedBy);
    List<PatientImportEntity> findByTargetMeasureIdOrderByCreatedAtDesc(Long measureId);

    // Tenant-scoped variants (Phase 2 — #698 import-domain enforcement)
    List<PatientImportEntity> findByTenantIdAndImportedByOrderByCreatedAtDesc(Long tenantId, String importedBy);
    List<PatientImportEntity> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
