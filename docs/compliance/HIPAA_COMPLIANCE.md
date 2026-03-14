# HIPAA Compliance Documentation

## CQL Platform — Health Insurance Portability and Accountability Act (HIPAA) Compliance

**Document Version**: 1.0
**Last Updated**: 2026-02-22
**Classification**: Confidential
**Owner**: CQL Platform Security Team

---

## Table of Contents

1. [Overview](#1-overview)
2. [HIPAA Applicability](#2-hipaa-applicability)
3. [Administrative Safeguards (§164.308)](#3-administrative-safeguards-164308)
4. [Physical Safeguards (§164.310)](#4-physical-safeguards-164310)
5. [Technical Safeguards (§164.312)](#5-technical-safeguards-164312)
6. [Organizational Requirements (§164.314)](#6-organizational-requirements-164314)
7. [Breach Notification (§164.400–414)](#7-breach-notification-164400414)
8. [Business Associate Agreement (BAA)](#8-business-associate-agreement-baa)
9. [Gap Analysis & Remediation Plan](#9-gap-analysis--remediation-plan)

---

## 1. Overview

The CQL Platform is a Clinical Quality Language (CQL) execution and electronic Quality Measure System (eQMS) designed for healthcare organizations. It processes, stores, and transmits Protected Health Information (PHI) including FHIR-based patient records, clinical observations, and quality measurement data.

This document maps the platform's technical and administrative controls to HIPAA Security Rule requirements under 45 CFR Parts 160 and 164.

### Scope

- **Backend**: Java Spring Boot application (REST API, CQL execution, FHIR integration)
- **Frontend**: React SPA (measure authoring, CDS Hooks, FHIR browser)
- **Infrastructure**: Docker Compose deployment with PostgreSQL, Nginx, HAPI FHIR, Prometheus/Grafana
- **Integrations**: FHIR R4 servers, VSAC terminology, Okta SSO, EHR/HIS connections

---

## 2. HIPAA Applicability

### PHI Data Elements Handled

| Data Element | Location | Protection |
|---|---|---|
| Patient demographics (name, DOB, address) | FHIR bundles in transit/memory | TLS in transit, not persisted at rest |
| Clinical observations | FHIR bundles during CQL execution | Processed in-memory, audit logged |
| Diagnoses & conditions | FHIR bundles during measure evaluation | Processed in-memory, audit logged |
| Email addresses | User entity (app_user table) | AES-256-GCM encryption at rest |
| Test case patient data | TestCase entity (JSON bundles) | Stored in database, date shifting available |

### Data Flow Classification

```
[EHR/FHIR Server] --TLS--> [CQL Platform Backend] --TLS--> [Frontend Browser]
                                    |
                                    ├── [PostgreSQL] (persistent storage)
                                    ├── [Audit Log] (365-day retention)
                                    └── [HAPI FHIR] (optional local server)
```

---

## 3. Administrative Safeguards (§164.308)

### 3.1 Security Management Process (§164.308(a)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Risk Analysis | This document + penetration testing report | ✅ Implemented |
| Risk Management | Security controls documented herein | ✅ Implemented |
| Sanction Policy | Administrative policy (out-of-scope for application) | ⚠️ Organizational |
| Information System Activity Review | AuditFilter + AuditService with daily/weekly/monthly statistics | ✅ Implemented |

**Implementation Details**:
- `AuditFilter` (`security/AuditFilter.java`) logs all API requests with username, action, resource, IP, user-agent, and response time
- `AuditService` (`service/AuditService.java`) provides search, statistics, and export capabilities
- Audit retention: 365 days with automated cleanup (daily at 02:00)
- PHI access tracked separately via FHIR patient data access queries

### 3.2 Assigned Security Responsibility (§164.308(a)(2))

| Requirement | Implementation | Status |
|---|---|---|
| Security Officer designation | Organizational responsibility | ⚠️ Organizational |

### 3.3 Workforce Security (§164.308(a)(3))

| Requirement | Implementation | Status |
|---|---|---|
| Authorization/Supervision | RBAC with 3 roles (ADMIN, USER, DEPARTMENT_ADMIN) | ✅ Implemented |
| Workforce Clearance | Admin user management via AdminController | ✅ Implemented |
| Termination Procedures | Account disable (enabled=false), API key revocation | ✅ Implemented |

**Implementation Details**:
- `OwnershipVerifier` (`security/OwnershipVerifier.java`) enforces resource-level access control
- Admin can disable user accounts via PUT `/api/admin/users/{id}/enabled`
- API keys can be revoked (active=false) without deletion
- Department-level isolation via DEPARTMENT_ADMIN role

### 3.4 Information Access Management (§164.308(a)(4))

| Requirement | Implementation | Status |
|---|---|---|
| Access Authorization | Role-based access in SecurityConfig | ✅ Implemented |
| Access Establishment/Modification | AdminController user CRUD operations | ✅ Implemented |

**Authorization Matrix**:

| Resource | USER | DEPARTMENT_ADMIN | ADMIN |
|---|---|---|---|
| Own measures | CRUD | CRUD | CRUD |
| Department measures | Read | CRUD | CRUD |
| User management | — | Department users | All users |
| Audit logs | — | — | Full access |
| System configuration | — | — | Full access |
| FHIR data access | Read | Read | Full |

### 3.5 Security Awareness & Training (§164.308(a)(5))

| Requirement | Implementation | Status |
|---|---|---|
| Security Reminders | Application-level help content (helpContent.ts) | ⚠️ Partial |
| Log-in Monitoring | Login activity tracking in AuditService | ✅ Implemented |
| Password Management | Force password change, reset flow | ✅ Implemented |

### 3.6 Security Incident Procedures (§164.308(a)(6))

| Requirement | Implementation | Status |
|---|---|---|
| Response & Reporting | Audit log monitoring, on-call runbooks | ✅ Implemented |

**Runbooks**:
- `docs/runbooks/on-call-guide.md` — incident response procedures
- `docs/runbooks/fhir-server-unavailable.md` — FHIR dependency failures
- `docs/runbooks/cql-execution-timeout.md` — execution timeouts
- `docs/runbooks/high-memory-usage.md` — resource exhaustion

### 3.7 Contingency Plan (§164.308(a)(7))

| Requirement | Implementation | Status |
|---|---|---|
| Data Backup Plan | PostgreSQL WAL archiving, Docker volumes | ✅ Implemented |
| Disaster Recovery Plan | See BACKUP_RECOVERY.md | ✅ Documented |
| Emergency Mode Operation | Graceful shutdown (35s grace), health checks | ✅ Implemented |
| Testing & Revision | Quarterly backup verification recommended | ⚠️ Schedule needed |

### 3.8 Evaluation (§164.308(a)(8))

| Requirement | Implementation | Status |
|---|---|---|
| Periodic evaluation | Penetration testing, code review | ✅ See PENETRATION_TEST_REPORT.md |

### 3.9 BAA Contracts (§164.308(b)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Written contract/arrangement | BAA template included in Section 8 | ✅ Template provided |

---

## 4. Physical Safeguards (§164.310)

> Physical safeguards are primarily the responsibility of the hosting environment (cloud provider, data center). The following application-level controls supplement physical security.

### 4.1 Facility Access Controls (§164.310(a)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Contingency operations | Docker-based deployment, portable infrastructure | ✅ Implemented |
| Facility security plan | Cloud provider responsibility | ⚠️ Organizational |
| Access control & validation | Cloud provider responsibility | ⚠️ Organizational |
| Maintenance records | Infrastructure-as-code (docker-compose.yml) | ✅ Implemented |

### 4.2 Workstation Use & Security (§164.310(b)–(c))

| Requirement | Implementation | Status |
|---|---|---|
| Workstation use policies | Organizational policy | ⚠️ Organizational |
| Workstation security | Organizational policy | ⚠️ Organizational |

### 4.3 Device & Media Controls (§164.310(d)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Disposal | Database encryption, container isolation | ✅ Implemented |
| Media re-use | Container ephemeral storage | ✅ Implemented |
| Accountability | Docker volume management, named volumes | ✅ Implemented |
| Data backup & storage | PostgreSQL WAL, Docker volume backups | ✅ Implemented |

---

## 5. Technical Safeguards (§164.312)

### 5.1 Access Control (§164.312(a)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Unique User Identification | Username-based identity, unique constraint on app_user.username | ✅ Implemented |
| Emergency Access Procedure | Admin password reset, force password change | ✅ Implemented |
| Automatic Logoff | JWT expiration (24h), stateless sessions | ✅ Implemented |
| Encryption & Decryption | AES-256-GCM for sensitive fields, TLS for transport | ✅ Implemented |

**JWT Authentication Details**:
- Algorithm: HMAC-SHA256 with ≥256-bit secret key
- Expiration: 24 hours (configurable via `jwt.expiration-ms`)
- Stateless: No server-side session storage
- Claims: username (sub), role, department, iat, exp
- Implementation: `JwtTokenProvider` (`security/JwtTokenProvider.java`)

**Encryption at Rest Details**:
- Algorithm: AES/GCM/NoPadding (256-bit key)
- Key derivation: PBKDF2WithHmacSHA256, 65,536 iterations
- IV: 12 bytes (96-bit), randomly generated per encryption
- GCM tag: 128 bits
- Applied to: email fields via `EncryptionConverter` JPA converter
- Key source: ENCRYPTION_KEY environment variable
- Implementation: `EncryptionConverter` (`security/EncryptionConverter.java`)

### 5.2 Audit Controls (§164.312(b))

| Requirement | Implementation | Status |
|---|---|---|
| Hardware/software/procedural audit | Comprehensive audit logging system | ✅ Implemented |

**Audit Infrastructure**:

| Component | Details |
|---|---|
| AuditFilter | HTTP-level request/response logging for all /api/* endpoints |
| AuditLogEntity | Persistent storage: username, method, path, resourceType, resourceId, action, statusCode, ipAddress, userAgent, responseTimeMs, createdAt |
| AuditService | Search, filter, statistics, export, retention management |
| MeasureAuditEntity | Measure-specific audit trail (CREATE, UPDATE, DELETE, LOCK, UNLOCK, SHARE, STATUS_CHANGE) |
| Indexes | audit_log: idx_audit_username, idx_audit_created, idx_audit_resource |
| Retention | 365 days (configurable), automated daily cleanup at 02:00 |
| Export | Up to 10,000 records per export operation |

### 5.3 Integrity (§164.312(c)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Mechanism to authenticate ePHI | Database integrity constraints, FHIR validation | ✅ Implemented |

**Data Integrity Controls**:
- PostgreSQL data checksums enabled (`-k` flag in docker-compose)
- Flyway versioned migrations with checksums
- Input validation: `InputValidator` with FHIR resource type allowlists, safe ID patterns, URL validation
- XSS protection: multi-layer (XssFilter, NoXss annotation, XssStringDeserializer)
- FHIR bundle validation during CQL execution

### 5.4 Person or Entity Authentication (§164.312(d))

| Requirement | Implementation | Status |
|---|---|---|
| Verify identity | JWT + BCrypt password, Okta SSO (OIDC), API keys | ✅ Implemented |

**Authentication Methods**:

| Method | Use Case | Implementation |
|---|---|---|
| Username/Password | Primary login | BCrypt hashing, JWT issuance |
| Okta OIDC SSO | Enterprise SSO | RS256 token validation, JIT provisioning |
| API Key | Programmatic CDS access | 32-byte random keys, prefix "cql_" |
| Bearer Token | API authentication | JWT in Authorization header |
| Query Parameter Token | SSE streams | Fallback for EventSource (no header support) |

### 5.5 Transmission Security (§164.312(e)(1))

| Requirement | Implementation | Status |
|---|---|---|
| Integrity controls | TLS encryption, HSTS header | ✅ Implemented |
| Encryption | HTTPS (TLS 1.2+), Nginx reverse proxy | ✅ Implemented |

**Transport Security**:
- Nginx HSTS: `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- Spring Security HSTS: enabled in SecurityConfig
- TLS termination: Nginx reverse proxy (keystore.p12 configuration available)
- CORS: Restricted to specific allowed origins
- CSP: `default-src 'self'` with minimal exceptions
- FHIR server communication: TLS with configurable SSL mode

---

## 6. Organizational Requirements (§164.314)

### 6.1 Business Associate Contracts (§164.314(a))

All third-party services that process, store, or transmit PHI require a Business Associate Agreement. See Section 8 for BAA template.

**Third-Party Services Requiring BAA**:

| Service | PHI Exposure | BAA Required |
|---|---|---|
| Cloud hosting provider | All platform data | ✅ Yes |
| FHIR server provider | Patient clinical data | ✅ Yes |
| Okta (SSO) | User identity data | ✅ Yes |
| VSAC/NLM | Terminology codes (no PHI) | ❌ No |
| SMTP provider | Password reset emails (no PHI in email body) | ⚠️ Review |

### 6.2 Requirements for Group Health Plans (§164.314(b))

Not applicable — CQL Platform is not a group health plan.

---

## 7. Breach Notification (§164.400–414)

### 7.1 Breach Detection Capabilities

| Capability | Implementation |
|---|---|
| Unauthorized access detection | Audit log analysis (failed auth, unusual access patterns) |
| Data exfiltration detection | Rate limiting (60 req/min), audit log export caps (10,000 records) |
| Anomaly detection | AuditService statistics: active users, top users, action distribution |
| Real-time alerting | Prometheus + AlertManager integration |

### 7.2 Breach Notification Procedure

1. **Discovery**: Detect via audit log review, monitoring alerts, or user report
2. **Investigation**: Review audit logs for scope (affected individuals, data types, timeframe)
3. **Risk Assessment**: Determine if breach involves unsecured PHI per 45 CFR §164.402
4. **Notification** (within 60 days of discovery):
   - Individual notification to affected persons
   - HHS notification (via HHS breach portal)
   - Media notification (if >500 individuals in a state/jurisdiction)
5. **Remediation**: Patch vulnerability, rotate credentials, update access controls
6. **Documentation**: Record breach details, investigation, notifications, remediation

### 7.3 Breach Log

Maintain a log of all breaches and near-misses. Template:

| Date | Description | PHI Involved | Individuals Affected | Root Cause | Remediation | Notifications Sent |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

---

## 8. Business Associate Agreement (BAA)

### BAA Template

> **Note**: This is a template. It must be reviewed and customized by legal counsel before execution.

---

**BUSINESS ASSOCIATE AGREEMENT**

This Business Associate Agreement ("Agreement") is entered into as of _____________ ("Effective Date") by and between:

**Covered Entity**: _________________________ ("Covered Entity")
**Business Associate**: CQL Platform Operator ("Business Associate")

**RECITALS**

WHEREAS, the Covered Entity uses the CQL Platform for clinical quality measure evaluation, CDS Hooks services, and related healthcare data processing;

WHEREAS, the Business Associate may create, receive, maintain, or transmit Protected Health Information ("PHI") on behalf of the Covered Entity;

**ARTICLE 1 — DEFINITIONS**

Terms used herein shall have the meanings set forth in 45 CFR Parts 160 and 164.

**ARTICLE 2 — OBLIGATIONS OF BUSINESS ASSOCIATE**

2.1. **Permitted Uses**: Business Associate shall use or disclose PHI only as permitted by this Agreement, the HIPAA Privacy Rule, and applicable law.

2.2. **Safeguards**: Business Associate shall implement administrative, physical, and technical safeguards as described in the CQL Platform HIPAA Compliance Documentation, including:
   - AES-256-GCM encryption at rest for sensitive fields
   - TLS 1.2+ encryption in transit
   - Role-based access control (RBAC)
   - Comprehensive audit logging (365-day retention)
   - JWT-based authentication with 24-hour token expiration
   - Rate limiting (60 requests/minute)
   - XSS protection (multi-layer)
   - Input validation (FHIR resource types, SSRF prevention)

2.3. **Breach Notification**: Business Associate shall report any Breach of Unsecured PHI to the Covered Entity without unreasonable delay and in no event later than 30 calendar days after discovery.

2.4. **Subcontractors**: Business Associate shall ensure that any subcontractor that creates, receives, maintains, or transmits PHI agrees to the same restrictions and conditions.

2.5. **Access**: Business Associate shall make PHI available to Covered Entity as required under 45 CFR §164.524.

2.6. **Amendment**: Business Associate shall make PHI available for amendment as required under 45 CFR §164.526.

2.7. **Accounting of Disclosures**: Business Associate shall make information available for accounting of disclosures as required under 45 CFR §164.528, facilitated by the CQL Platform audit logging system.

2.8. **HHS Access**: Business Associate shall make internal practices, books, and records available to the Secretary of HHS for determining compliance.

**ARTICLE 3 — PERMITTED USES AND DISCLOSURES**

3.1. Business Associate may use or disclose PHI for:
   - CQL execution and quality measure evaluation
   - CDS Hooks decision support services
   - FHIR data browsing and terminology services
   - Test case management (with date shifting for de-identification)
   - Platform administration and technical support

3.2. Business Associate may de-identify PHI in accordance with 45 CFR §164.514.

**ARTICLE 4 — OBLIGATIONS OF COVERED ENTITY**

4.1. Covered Entity shall notify Business Associate of any restrictions on PHI use/disclosure.
4.2. Covered Entity shall not request uses or disclosures that would violate the HIPAA Privacy Rule.

**ARTICLE 5 — TERM AND TERMINATION**

5.1. **Term**: This Agreement shall be effective as of the Effective Date and shall terminate when all PHI is destroyed or returned.
5.2. **Termination for Cause**: Either party may terminate upon 30 days' written notice if the other party materially breaches this Agreement.
5.3. **Effect of Termination**: Upon termination, Business Associate shall return or destroy all PHI. If return or destruction is not feasible, protections of this Agreement shall extend.

**ARTICLE 6 — MISCELLANEOUS**

6.1. **Regulatory References**: All references to HIPAA shall include amendments, including the HITECH Act and Omnibus Rule.
6.2. **Amendment**: This Agreement may be amended by mutual written consent.
6.3. **Survival**: Obligations under this Agreement shall survive termination.

---

**COVERED ENTITY**

By: ___________________________
Name: _________________________
Title: _________________________
Date: _________________________

**BUSINESS ASSOCIATE**

By: ___________________________
Name: _________________________
Title: _________________________
Date: _________________________

---

## 9. Gap Analysis & Remediation Plan

### Current Gaps

| # | Gap | HIPAA Requirement | Severity | Remediation |
|---|---|---|---|---|
| 1 | No Multi-Factor Authentication (MFA) | §164.312(d) — additional verification | High | Implement TOTP/WebAuthn MFA for admin and PHI access |
| 2 | No token revocation on logout | §164.312(a)(1)(iii) — automatic logoff | Medium | Add JWT blacklist (Redis-backed) or reduce token lifetime to 1 hour |
| 3 | Encryption key rotation absent | §164.312(a)(2)(iv) — encryption | Medium | Implement key versioning in EncryptionConverter |
| 4 | Password complexity insufficient | §164.308(a)(5)(ii)(D) — password management | Medium | Enforce: 12+ chars, uppercase, lowercase, digit, special char |
| 5 | TLS not enforced in dev | §164.312(e)(1) — transmission security | Low | Enable TLS in all environments; use self-signed certs in dev |
| 6 | No formal security training program | §164.308(a)(5) — security awareness | Low | Establish quarterly security training schedule |
| 7 | Backup verification not scheduled | §164.308(a)(7)(ii)(D) — testing & revision | Low | Schedule quarterly backup restoration tests |

### Remediation Timeline

| Priority | Items | Target |
|---|---|---|
| P0 (Critical) | #1 MFA implementation | Q2 2026 |
| P1 (High) | #2 Token revocation, #4 Password complexity | Q2 2026 |
| P2 (Medium) | #3 Key rotation, #5 TLS enforcement | Q3 2026 |
| P3 (Low) | #6 Training program, #7 Backup testing | Q3 2026 |

---

## Appendix A: Security Control Inventory

| Control | File/Component | Description |
|---|---|---|
| Authentication | `security/JwtTokenProvider.java` | HMAC-SHA256 JWT with ≥256-bit key |
| SSO | `service/OktaOidcService.java` | Okta OIDC with RS256 validation |
| Password Hashing | `config/SecurityConfig.java` | BCryptPasswordEncoder |
| Encryption at Rest | `security/EncryptionConverter.java` | AES-256-GCM with PBKDF2 key derivation |
| Audit Logging | `security/AuditFilter.java` | HTTP request/response audit trail |
| Measure Audit | `entity/MeasureAuditEntity.java` | Measure lifecycle change tracking |
| XSS Prevention | `security/XssFilter.java` | Request-level HTML entity encoding |
| XSS Validation | `security/NoXssValidator.java` | Field-level XSS pattern detection |
| XSS Deserialization | `security/XssStringDeserializer.java` | Jackson-level sanitization |
| RBAC | `security/OwnershipVerifier.java` | Role + ownership verification |
| Rate Limiting | `security/RateLimitFilter.java` | Token bucket, 60 req/min/IP |
| Input Validation | `security/InputValidator.java` | FHIR types, IDs, URLs, SSRF prevention |
| API Keys | `service/UserApiKeyService.java` | 32-byte random keys with revocation |
| Password Reset | `service/PasswordResetService.java` | SHA-256 hashed tokens, 30-min expiry |
| Security Headers | `config/SecurityConfig.java` | HSTS, CSP, X-Frame-Options, etc. |
| CORS | `config/WebConfig.java` | Origin allowlist with PNA support |

## Appendix B: Applicable Regulations

- **HIPAA Security Rule**: 45 CFR Part 164, Subpart C
- **HIPAA Privacy Rule**: 45 CFR Part 164, Subpart E
- **HITECH Act**: Subtitle D of Title XIII, ARRA 2009
- **HIPAA Omnibus Rule**: 78 FR 5566 (2013)
- **Taiwan PDPA**: Personal Data Protection Act (個人資料保護法) — applicable for Taiwan deployments
