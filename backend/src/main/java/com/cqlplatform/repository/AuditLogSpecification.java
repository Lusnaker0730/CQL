package com.cqlplatform.repository;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.model.audit.AuditLogSearchRequest;
import com.cqlplatform.security.InputValidator;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLogEntity> fromSearchRequest(AuditLogSearchRequest request) {
        Specification<AuditLogEntity> spec = Specification.where(null);

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            spec = spec.and(usernameContains(request.getUsername()));
        }
        if (request.getAction() != null && !request.getAction().isBlank()) {
            spec = spec.and(actionEquals(request.getAction()));
        }
        if (request.getResourceType() != null && !request.getResourceType().isBlank()) {
            spec = spec.and(resourceTypeEquals(request.getResourceType()));
        }
        if (request.getStatusCode() != null) {
            spec = spec.and(statusCodeEquals(request.getStatusCode()));
        }
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            LocalDateTime start = LocalDate.parse(request.getStartDate()).atStartOfDay();
            spec = spec.and(createdAfter(start));
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            LocalDateTime end = LocalDate.parse(request.getEndDate()).atTime(LocalTime.MAX);
            spec = spec.and(createdBefore(end));
        }
        if (request.getConnectionId() != null) {
            spec = spec.and(connectionIdEquals(request.getConnectionId()));
        }
        if (request.getPatientFhirId() != null && !request.getPatientFhirId().isBlank()) {
            spec = spec.and(patientFhirIdEquals(request.getPatientFhirId()));
        }
        if (request.getPhiAccess() != null) {
            spec = spec.and(phiAccessEquals(request.getPhiAccess()));
        }

        return spec;
    }

    private static Specification<AuditLogEntity> usernameContains(String username) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("username")), "%" + InputValidator.escapeLikeWildcards(username.toLowerCase()) + "%");
    }

    private static Specification<AuditLogEntity> actionEquals(String action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    private static Specification<AuditLogEntity> resourceTypeEquals(String resourceType) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("resourceType")), "%" + InputValidator.escapeLikeWildcards(resourceType.toLowerCase()) + "%");
    }

    private static Specification<AuditLogEntity> statusCodeEquals(Integer statusCode) {
        return (root, query, cb) -> cb.equal(root.get("statusCode"), statusCode);
    }

    private static Specification<AuditLogEntity> createdAfter(LocalDateTime after) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), after);
    }

    private static Specification<AuditLogEntity> createdBefore(LocalDateTime before) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), before);
    }

    private static Specification<AuditLogEntity> connectionIdEquals(Long connectionId) {
        return (root, query, cb) -> cb.equal(root.get("connectionId"), connectionId);
    }

    private static Specification<AuditLogEntity> patientFhirIdEquals(String patientFhirId) {
        return (root, query, cb) -> cb.equal(root.get("patientFhirId"), patientFhirId);
    }

    private static Specification<AuditLogEntity> phiAccessEquals(Boolean phiAccess) {
        return (root, query, cb) -> cb.equal(root.get("phiAccess"), phiAccess);
    }
}
