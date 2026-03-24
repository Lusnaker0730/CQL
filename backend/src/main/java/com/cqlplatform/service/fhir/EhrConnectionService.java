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

    @Transactional(readOnly = true)
    public List<EhrConnectionEntity> list(String department) {
        if (department != null && !department.isBlank()) {
            return repository.findByDepartmentAndActiveTrue(department);
        }
        return repository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public EhrConnectionEntity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EHR connection not found: " + id));
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
        connection.setStatus("untested");
        EhrConnectionEntity saved = repository.save(connection);
        log.info("Created EHR connection '{}' (id={})", saved.getName(), saved.getId());
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

    public EhrConnectionEntity testConnection(Long id) {
        EhrConnectionEntity connection = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EHR connection not found: " + id));
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
