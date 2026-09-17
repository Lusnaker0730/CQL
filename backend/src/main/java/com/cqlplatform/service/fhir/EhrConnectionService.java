package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.model.ehr.EhrConnectionRequest;
import com.cqlplatform.repository.EhrConnectionRepository;
import com.cqlplatform.security.InputValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EhrConnectionService {

    private final EhrConnectionRepository repository;
    private final FhirClientFactory fhirClientFactory;
    private final SmartBackendTokenService smartBackendTokenService;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<EhrConnectionEntity> list(String department) {
        Long tenantId = effectiveTenantId();
        if (department != null && !department.isBlank()) {
            return repository.findByTenantIdAndDepartmentAndActiveTrue(tenantId, department);
        }
        return repository.findByTenantIdAndActiveTrue(tenantId);
    }

    /**
     * Tenant-scoped lookup for MANAGEMENT operations (request thread). A connection in
     * another tenant reads as not-found. Do NOT use from async execution paths — see
     * {@link #getByIdUnscoped}.
     */
    @Transactional(readOnly = true)
    public EhrConnectionEntity getById(Long id) {
        return repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("EHR connection not found: " + id));
    }

    /**
     * Unscoped lookup — used only by the CQL/eCQM execution path, which may run on async
     * executor threads where the request-thread TenantContext is not visible. That path is
     * still gated by the interim ADMIN/DEPARTMENT_ADMIN guard; execute-path tenant scoping
     * (with async tenant propagation) is a Phase 2 follow-up.
     */
    @Transactional(readOnly = true)
    public EhrConnectionEntity getByIdUnscoped(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EHR connection not found: " + id));
    }

    /** Effective tenant: the caller's, or the default tenant for legacy callers with none. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @Transactional
    public EhrConnectionEntity create(EhrConnectionRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setName(request.getName());
        connection.setFhirServerUrl(request.getFhirServerUrl());
        connection.setAuthType(request.getAuthType() != null ? request.getAuthType() : "none");
        connection.setCredentials(request.getCredentials());
        connection.setDepartment(request.getDepartment());
        connection.setTokenEndpoint(request.getTokenEndpoint());
        if (request.getTlsEnabled() != null) {
            connection.setTlsEnabled(request.getTlsEnabled());
        }
        connection.setCaCertPem(request.getCaCertPem());
        connection.setClientCertPem(request.getClientCertPem());
        connection.setClientKeyPem(request.getClientKeyPem());
        if (request.getTlsMinVersion() != null) {
            connection.setTlsMinVersion(request.getTlsMinVersion());
        }
        if (request.getHostnameVerification() != null) {
            connection.setHostnameVerification(request.getHostnameVerification());
        }
        connection.setStatus("untested");
        // Assign the connection to the caller's tenant server-side (never client-supplied).
        connection.setTenantId(effectiveTenantId());
        EhrConnectionEntity saved = repository.save(connection);
        log.info("Created EHR connection '{}' (id={}, tenant={})", saved.getName(), saved.getId(), saved.getTenantId());
        return saved;
    }

    @Transactional
    public EhrConnectionEntity update(Long id, EhrConnectionRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        EhrConnectionEntity existing = getById(id);
        existing.setName(request.getName());
        existing.setFhirServerUrl(request.getFhirServerUrl());
        existing.setAuthType(request.getAuthType() != null ? request.getAuthType() : "none");
        existing.setCredentials(request.getCredentials());
        existing.setDepartment(request.getDepartment());
        existing.setTokenEndpoint(request.getTokenEndpoint());
        if (request.getTlsEnabled() != null) {
            existing.setTlsEnabled(request.getTlsEnabled());
        }
        existing.setCaCertPem(request.getCaCertPem());
        existing.setClientCertPem(request.getClientCertPem());
        existing.setClientKeyPem(request.getClientKeyPem());
        if (request.getTlsMinVersion() != null) {
            existing.setTlsMinVersion(request.getTlsMinVersion());
        }
        if (request.getHostnameVerification() != null) {
            existing.setHostnameVerification(request.getHostnameVerification());
        }
        // Reset status when URL or auth changes
        existing.setStatus("untested");
        existing.setLastTestedAt(null);
        existing.setLastTestMessage(null);
        smartBackendTokenService.evictToken(id);
        EhrConnectionEntity saved = repository.save(existing);
        log.info("Updated EHR connection '{}' (id={})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        EhrConnectionEntity existing = getById(id);
        existing.setActive(false);
        repository.save(existing);
        smartBackendTokenService.evictToken(id);
        log.info("Soft-deleted EHR connection '{}' (id={})", existing.getName(), id);
    }

    @Transactional
    public EhrConnectionEntity testConnection(Long id) {
        // PAT-142: read-modify-save sequence (findById → setStatus/setLastTestedAt/
        // setLastTestMessage → save) needs an explicit transactional boundary so the
        // success and failure paths atomically commit; same convention as the
        // create / update / delete methods in this class.
        // BUG-138: tenant-scoped, matching update() / delete(). The raw findById let a clinic
        // ADMIN/DEPARTMENT_ADMIN test ANY tenant's connection — which both mutates its status
        // fields and hands back the entity, credentials and cert material included.
        EhrConnectionEntity connection = getById(id);
        try {
            IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);
            CapabilityStatement capabilities = client.capabilities()
                    .ofType(CapabilityStatement.class)
                    .execute();

            String serverName = capabilities.getSoftware() != null
                    ? capabilities.getSoftware().getName()
                    : "Unknown";
            String fhirVersion = capabilities.getFhirVersion() != null
                    ? capabilities.getFhirVersion().toCode()
                    : "Unknown";

            connection.setStatus("connected");
            connection.setLastTestedAt(LocalDateTime.now());
            connection.setLastTestMessage("Connected successfully. Server: " + serverName + ", FHIR version: " + fhirVersion);
            log.info("EHR connection '{}' test succeeded: {} (FHIR {})", connection.getName(), serverName, fhirVersion);
        } catch (Exception e) {
            connection.setStatus("error");
            connection.setLastTestedAt(LocalDateTime.now());
            String message = e.getMessage();
            if (message != null && message.length() > 490) {
                message = message.substring(0, 490) + "...";
            }
            connection.setLastTestMessage("Connection failed: " + message);
            log.warn("EHR connection '{}' test failed: {}", connection.getName(), e.getMessage(), e);
        }
        return repository.save(connection);
    }
}
