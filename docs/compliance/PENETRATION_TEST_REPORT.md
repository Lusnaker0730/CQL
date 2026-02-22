# Penetration Testing Report

## CQL Platform — Security Assessment Report

**Document Version**: 1.0
**Assessment Date**: 2026-02-22
**Classification**: Confidential
**Methodology**: OWASP Testing Guide v4.2, OWASP Top 10 (2021)
**Scope**: Full-stack application (Backend API + Frontend SPA + Infrastructure)
**Assessor**: CQL Platform Security Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Scope & Methodology](#2-scope--methodology)
3. [Findings Summary](#3-findings-summary)
4. [Detailed Findings](#4-detailed-findings)
5. [OWASP Top 10 Coverage](#5-owasp-top-10-coverage)
6. [Authentication & Session Management](#6-authentication--session-management)
7. [Authorization & Access Control](#7-authorization--access-control)
8. [Data Protection](#8-data-protection)
9. [Input Validation & Injection](#9-input-validation--injection)
10. [API Security](#10-api-security)
11. [Infrastructure Security](#11-infrastructure-security)
12. [Positive Findings](#12-positive-findings)
13. [Recommendations](#13-recommendations)
14. [Appendices](#14-appendices)

---

## 1. Executive Summary

### Overall Risk Rating: **MEDIUM**

The CQL Platform demonstrates a strong security posture with defense-in-depth controls across authentication, authorization, input validation, and audit logging. The assessment identified **3 medium-severity** and **5 low-severity** findings, with **0 critical** and **0 high** severity vulnerabilities.

### Key Strengths
- Multi-layer XSS protection (filter, validation annotation, Jackson deserializer)
- Comprehensive audit logging with 365-day retention
- Role-based access control with ownership verification
- AES-256-GCM encryption at rest for sensitive fields
- Rate limiting (60 req/min per IP)
- Input validation with SSRF prevention
- Resilience4j circuit breakers preventing cascading failures

### Key Findings Requiring Attention
- No Multi-Factor Authentication (MFA)
- JWT tokens not revocable on logout (24-hour validity)
- Password complexity policy insufficient (6-character minimum only)
- Encryption key rotation mechanism absent
- H2 database console accessible in development profile

### Risk Distribution

| Severity | Count | Description |
|---|---|---|
| Critical | 0 | Immediate exploitation risk |
| High | 0 | Significant risk requiring urgent remediation |
| Medium | 3 | Moderate risk, should be addressed within 90 days |
| Low | 5 | Minor risk, address as part of regular development |
| Informational | 4 | Best practice recommendations |

---

## 2. Scope & Methodology

### 2.1 Assessment Scope

| Component | Scope | Method |
|---|---|---|
| Backend API | All REST endpoints (`/api/*`, `/cds-services/*`) | Code review + dynamic testing |
| Frontend SPA | React application, state management, API calls | Code review |
| Authentication | JWT, Okta SSO, API keys, password reset | Code review + dynamic testing |
| Authorization | RBAC, ownership verification, department isolation | Code review + dynamic testing |
| Data protection | Encryption, XSS, input validation | Code review |
| Infrastructure | Docker, Nginx, PostgreSQL, monitoring | Configuration review |
| Dependencies | Maven (backend), npm (frontend) | Dependency analysis |

### 2.2 Methodology

- **OWASP Testing Guide v4.2**: Systematic testing of web application security
- **OWASP Top 10 (2021)**: Coverage of top web application risks
- **OWASP API Security Top 10**: API-specific security risks
- **Code Review**: Static analysis of security-critical components
- **Configuration Review**: Assessment of deployment configurations

### 2.3 Files Reviewed

| Category | Key Files |
|---|---|
| Security Config | `SecurityConfig.java`, `WebConfig.java` |
| Authentication | `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `AuthController.java` |
| Authorization | `OwnershipVerifier.java`, `CustomUserDetailsService.java` |
| Input Validation | `InputValidator.java`, `XssFilter.java`, `NoXssValidator.java`, `XssStringDeserializer.java` |
| Encryption | `EncryptionConverter.java` |
| Audit | `AuditFilter.java`, `AuditService.java` |
| Rate Limiting | `RateLimitFilter.java` |
| Infrastructure | `Dockerfile`, `docker-compose.yml`, `nginx.conf`, `application.yml` |

---

## 3. Findings Summary

| ID | Title | Severity | OWASP Category | Status |
|---|---|---|---|---|
| PT-001 | No Multi-Factor Authentication | Medium | A07:2021 | Open |
| PT-002 | JWT tokens not revocable on logout | Medium | A07:2021 | Open |
| PT-003 | Weak password complexity policy | Medium | A07:2021 | Open |
| PT-004 | No encryption key rotation mechanism | Low | A02:2021 | Open |
| PT-005 | H2 console accessible in dev profile | Low | A05:2021 | Accepted |
| PT-006 | Static PBKDF2 salt for encryption | Low | A02:2021 | Open |
| PT-007 | CORS allows localhost origins | Low | A05:2021 | Accepted (dev) |
| PT-008 | JWT secret minimum length not validated at startup | Low | A02:2021 | Mitigated |
| PT-INF-001 | Consider Content-Security-Policy nonce for inline scripts | Info | — | Noted |
| PT-INF-002 | Structured logging could include request body for forensics | Info | — | Noted |
| PT-INF-003 | Consider implementing Subresource Integrity (SRI) | Info | — | Noted |
| PT-INF-004 | Password history tracking not implemented | Info | — | Noted |

---

## 4. Detailed Findings

### PT-001: No Multi-Factor Authentication (MFA)

**Severity**: Medium
**OWASP**: A07:2021 — Identification and Authentication Failures
**CVSS 3.1**: 5.3 (Medium)

**Description**:
The platform relies on single-factor authentication (username/password or Okta SSO). There is no TOTP, WebAuthn, or SMS-based second factor. For a healthcare application handling PHI, MFA is strongly recommended by HIPAA §164.312(d) and required by many healthcare compliance frameworks.

**Evidence**:
- `AuthController.java`: `/api/auth/login` accepts only username and password
- No MFA-related entities, services, or endpoints exist
- Okta SSO may provide MFA if configured at the IdP level, but this is not enforced by the application

**Impact**: An attacker who compromises user credentials (phishing, credential stuffing, password reuse) gains full access without additional verification.

**Recommendation**:
1. Implement TOTP-based MFA using a library like `dev.samstevens.totp` or `com.warrenstrange.googleauth`
2. Add MFA entity (user_mfa_settings) with encrypted TOTP secret
3. Add enrollment, verification, and recovery code endpoints
4. Require MFA for ADMIN and DEPARTMENT_ADMIN roles at minimum
5. Alternatively, enforce MFA at the Okta SSO level for all users

---

### PT-002: JWT Tokens Not Revocable on Logout

**Severity**: Medium
**OWASP**: A07:2021 — Identification and Authentication Failures
**CVSS 3.1**: 4.8 (Medium)

**Description**:
JWT tokens have a 24-hour expiration (`jwt.expiration-ms=86400000`) and are stateless. Once issued, they cannot be revoked. The frontend may clear the token from local storage, but the token remains valid server-side until expiration.

**Evidence**:
- `JwtTokenProvider.java`: `validateToken()` only checks signature and expiration
- No token blacklist, revocation list, or server-side session store exists
- `AuthController.java`: No `/api/auth/logout` endpoint that invalidates tokens

**Impact**: If a token is stolen (XSS, MITM, shared device), it remains valid for up to 24 hours regardless of user action. Account compromise persists even after password change.

**Recommendation**:
1. **Option A (Preferred)**: Reduce token lifetime to 15-60 minutes and implement refresh tokens
2. **Option B**: Add a Redis-backed JWT blacklist; on logout or password change, add token JTI to blacklist
3. **Option C**: Implement server-side session tracking with a session store
4. Add `/api/auth/logout` endpoint that invalidates the current token
5. Invalidate all tokens on password change

---

### PT-003: Weak Password Complexity Policy

**Severity**: Medium
**OWASP**: A07:2021 — Identification and Authentication Failures
**CVSS 3.1**: 4.3 (Medium)

**Description**:
The password policy requires only a minimum of 6 characters with no complexity requirements (uppercase, lowercase, digits, special characters).

**Evidence**:
- `AuthController.java` registration endpoint: password length validation is 6 characters minimum
- No character class requirements (uppercase, lowercase, digit, special)
- No password history tracking
- No common password blocklist

**Impact**: Users can set weak passwords (e.g., "123456", "password") that are vulnerable to brute force and dictionary attacks. The rate limiter (60 req/min) provides some protection against online brute force but does not protect against offline attacks on leaked password hashes.

**Recommendation**:
1. Increase minimum length to 12 characters
2. Require at least 3 of 4 character classes: uppercase, lowercase, digit, special character
3. Implement a common password blocklist (NIST SP 800-63B recommendation)
4. Add password strength meter to the frontend
5. Consider implementing password history (prevent reuse of last 5 passwords)

---

### PT-004: No Encryption Key Rotation Mechanism

**Severity**: Low
**OWASP**: A02:2021 — Cryptographic Failures
**CVSS 3.1**: 3.7 (Low)

**Description**:
The `EncryptionConverter` uses a single key derived from the `ENCRYPTION_KEY` environment variable. There is no mechanism to rotate this key without downtime or data migration.

**Evidence**:
- `EncryptionConverter.java`: Key derived at initialization, no versioning
- Encrypted data format: `ENC:<base64(IV+ciphertext)>` — no key version identifier
- Changing ENCRYPTION_KEY would make all existing encrypted data unreadable

**Impact**: If the encryption key is compromised, all encrypted data is exposed with no way to re-encrypt without a maintenance window. Key compromise may go undetected since there's no rotation forcing function.

**Recommendation**:
1. Add key version prefix to encrypted data format: `ENC:v1:<base64(IV+ciphertext)>`
2. Support multiple active keys (current for encryption, previous for decryption)
3. Implement a background re-encryption job for key rotation
4. Schedule key rotation annually or after any suspected compromise

---

### PT-005: H2 Database Console Accessible in Development Profile

**Severity**: Low
**OWASP**: A05:2021 — Security Misconfiguration
**CVSS 3.1**: 3.1 (Low)

**Description**:
The H2 database console is enabled in the `dev` profile with frame options set to `SAMEORIGIN`, allowing access at `/h2-console`.

**Evidence**:
- `SecurityConfig.java`: H2 console conditionally enabled when profile is "dev"
- `application-dev.yml`: `spring.h2.console.enabled: true`
- Frame options set to `SAMEORIGIN` for H2 console path

**Impact**: In development environments, the H2 console provides direct SQL access to the database. If a development instance is accidentally exposed to the internet, this could lead to data access or manipulation.

**Mitigating Controls**:
- Only enabled in `dev` profile, not in `docker` or production
- Requires direct access to the server (localhost)
- Protected by Spring Security authentication

**Status**: Accepted — development convenience outweighs the risk given existing mitigating controls.

---

### PT-006: Static PBKDF2 Salt for Encryption Key Derivation

**Severity**: Low
**OWASP**: A02:2021 — Cryptographic Failures
**CVSS 3.1**: 3.1 (Low)

**Description**:
The `EncryptionConverter` uses a static salt string `"CQLPlatformEncryption"` for PBKDF2 key derivation. While a random IV is used per encryption operation (which is correct), the salt should ideally be unique per deployment.

**Evidence**:
```java
// EncryptionConverter.java
private static final String SALT = "CQLPlatformEncryption";
```

**Impact**: If two deployments use the same `ENCRYPTION_KEY`, they will derive the same encryption key. This is unlikely in practice but violates the principle of defense-in-depth.

**Recommendation**:
1. Derive salt from a deployment-specific value (e.g., hash of database URL + deployment ID)
2. Or use a separate `ENCRYPTION_SALT` environment variable
3. The 65,536 iteration count for PBKDF2 is appropriate

---

### PT-007: CORS Allows Localhost Origins

**Severity**: Low
**OWASP**: A05:2021 — Security Misconfiguration
**CVSS 3.1**: 2.4 (Low)

**Description**:
The CORS configuration allows `localhost:5173` and `localhost:8080` as origins in all profiles.

**Evidence**:
- `WebConfig.java`: Hardcoded localhost origins in allowed list
- Additional origins configurable via `cors.allowed-origins`

**Impact**: In production, allowing localhost origins could enable CSRF-like attacks from malicious localhost services, though this requires the attacker to have local access to the user's machine.

**Status**: Accepted for development. In production, the `cors.allowed-origins` property should be set to only the production domain.

---

### PT-008: JWT Secret Minimum Length Validated at Startup

**Severity**: Low
**OWASP**: A02:2021 — Cryptographic Failures
**CVSS 3.1**: 2.0 (Low)

**Description**:
The JWT secret key must be at least 32 characters (256 bits) for HMAC-SHA256. This is validated at application startup.

**Evidence**:
- `JwtTokenProvider.java`: Initialization validates key length ≥ 32 characters
- Uses `Keys.hmacShaKeyFor()` which enforces minimum key size

**Status**: Mitigated — the application correctly enforces minimum key size at startup and will fail to start with an insufficient key.

---

## 5. OWASP Top 10 Coverage

### A01:2021 — Broken Access Control

**Risk Level**: LOW — Well controlled

| Test | Result | Evidence |
|---|---|---|
| Vertical privilege escalation | ✅ Pass | SecurityConfig enforces role checks; ADMIN endpoints require ADMIN/DEPARTMENT_ADMIN |
| Horizontal privilege escalation | ✅ Pass | OwnershipVerifier checks resource ownership before access |
| IDOR (Insecure Direct Object Reference) | ✅ Pass | Ownership verification on measure CRUD, test cases, notifications |
| Missing function-level access control | ✅ Pass | Spring Security method-level authorization |
| CORS misconfiguration | ⚠️ Minor | Localhost allowed (PT-007, accepted for dev) |
| Directory traversal | ✅ Pass | No file system access exposed via API |
| Department isolation | ✅ Pass | DEPARTMENT_ADMIN scoped to own department |

### A02:2021 — Cryptographic Failures

**Risk Level**: LOW — Strong implementation with minor gaps

| Test | Result | Evidence |
|---|---|---|
| Encryption algorithm strength | ✅ Pass | AES-256-GCM (NIST approved) |
| Key derivation | ✅ Pass | PBKDF2, 65,536 iterations, 256-bit key |
| IV/Nonce management | ✅ Pass | 12-byte random IV per encryption |
| Password hashing | ✅ Pass | BCrypt (10 rounds) |
| TLS configuration | ✅ Pass | HSTS enabled, TLS template in config |
| Key management | ⚠️ Gap | No key rotation (PT-004) |
| Static salt | ⚠️ Minor | Static PBKDF2 salt (PT-006) |

### A03:2021 — Injection

**Risk Level**: VERY LOW — Comprehensive protection

| Test | Result | Evidence |
|---|---|---|
| SQL injection | ✅ Pass | JPA with parameterized queries (Spring Data) |
| XSS (reflected) | ✅ Pass | XssFilter HTML entity encoding on all parameters |
| XSS (stored) | ✅ Pass | NoXssValidator on input, XssStringDeserializer on JSON |
| XSS (DOM-based) | ✅ Pass | React's default JSX escaping |
| Command injection | ✅ Pass | No OS command execution in application |
| LDAP injection | ✅ N/A | No LDAP integration |
| SSRF | ✅ Pass | InputValidator blocks private IPs, validates URL schemes |
| CQL injection | ✅ Pass | CQL executed in sandboxed engine with timeout (120s) |

### A04:2021 — Insecure Design

**Risk Level**: LOW — Secure design patterns used

| Test | Result | Evidence |
|---|---|---|
| Threat modeling | ✅ Pass | Security controls aligned with threat model |
| Secure defaults | ✅ Pass | CSRF disabled (stateless API), secure headers default |
| Rate limiting | ✅ Pass | 60 req/min per IP with token bucket |
| Business logic flaws | ✅ Pass | Measure workflow state machine prevents invalid transitions |
| Resource exhaustion | ✅ Pass | CQL timeout (120s), Docker resource limits (2 CPU, 1GB) |

### A05:2021 — Security Misconfiguration

**Risk Level**: LOW — Well configured with minor items

| Test | Result | Evidence |
|---|---|---|
| Security headers | ✅ Pass | HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Permissions-Policy |
| Default credentials | ✅ Pass | No default admin account; credentials from env vars |
| Error handling | ✅ Pass | GlobalExceptionHandler returns safe error responses (no stack traces) |
| Unnecessary features | ⚠️ Minor | H2 console in dev (PT-005, accepted) |
| Server info disclosure | ✅ Pass | Nginx doesn't expose server version |
| Debug endpoints | ✅ Pass | Actuator limited to health/metrics/prometheus; /actuator blocked by Nginx |

### A06:2021 — Vulnerable and Outdated Components

**Risk Level**: LOW — Managed dependencies

| Test | Result | Evidence |
|---|---|---|
| Known vulnerabilities | ⚠️ Review | Spring Boot managed dependencies; manual version pinning for JJWT, Nimbus |
| Component inventory | ✅ Pass | Maven pom.xml and package.json track all dependencies |
| Update policy | ⚠️ Gap | No automated vulnerability scanning in CI |
| End-of-life components | ✅ Pass | Java 21 LTS, Spring Boot 3.x (current) |

### A07:2021 — Identification and Authentication Failures

**Risk Level**: MEDIUM — Functional but missing MFA

| Test | Result | Evidence |
|---|---|---|
| Brute force protection | ✅ Pass | Rate limiting (60 req/min per IP) |
| Credential stuffing | ⚠️ Gap | No account lockout after failed attempts |
| Password policy | ⚠️ Weak | 6-char minimum, no complexity (PT-003) |
| Multi-factor authentication | ❌ Missing | No MFA implementation (PT-001) |
| Session management | ⚠️ Gap | JWT not revocable on logout (PT-002) |
| Password storage | ✅ Pass | BCrypt with 10 rounds |
| Password reset | ✅ Pass | SHA-256 hashed tokens, 30-min expiry, one-time use |
| Email enumeration | ✅ Pass | Silent failure on forgot-password for unknown emails |

### A08:2021 — Software and Data Integrity Failures

**Risk Level**: VERY LOW

| Test | Result | Evidence |
|---|---|---|
| CI/CD pipeline integrity | ✅ Pass | Git-based version control, Flyway migration checksums |
| Deserialization attacks | ✅ Pass | Jackson with XssStringDeserializer; no Java serialization |
| Software supply chain | ⚠️ Review | No automated dependency scanning |
| Code signing | ✅ N/A | Docker image-based deployment |

### A09:2021 — Security Logging and Monitoring Failures

**Risk Level**: VERY LOW — Comprehensive logging

| Test | Result | Evidence |
|---|---|---|
| Audit logging | ✅ Pass | AuditFilter logs all API requests with user, action, resource, IP |
| Log completeness | ✅ Pass | All /api/* endpoints covered |
| Log retention | ✅ Pass | 365-day retention with automated cleanup |
| Log integrity | ✅ Pass | Database-backed, immutable (INSERT only for audit_log) |
| Monitoring & alerting | ✅ Pass | Prometheus + Grafana + AlertManager |
| PHI access logging | ✅ Pass | Dedicated PHI access tracking in AuditService |

### A10:2021 — Server-Side Request Forgery (SSRF)

**Risk Level**: LOW — Protected

| Test | Result | Evidence |
|---|---|---|
| URL validation | ✅ Pass | InputValidator.validateUrl() checks schemes and resolves hostnames |
| Private IP blocking | ✅ Pass | Blocks loopback, link-local, site-local, multicast addresses |
| Credential blocking | ✅ Pass | Rejects URLs with embedded userinfo |
| DNS rebinding | ⚠️ Partial | IP checked after resolution, but no re-resolution check |
| FHIR server URL validation | ✅ Pass | validateUrl() applied to FHIR server configuration |

---

## 6. Authentication & Session Management

### 6.1 Authentication Mechanisms

| Mechanism | Strength | Notes |
|---|---|---|
| Username/Password | Good | BCrypt hashing, JWT issuance |
| Okta SSO (OIDC) | Strong | RS256 token validation, JIT provisioning |
| API Keys | Good | 32-byte random, prefix-based, revocable |
| Password Reset | Good | SHA-256 hashed token, 30-min expiry, one-time use |

### 6.2 Session Security

| Property | Value | Assessment |
|---|---|---|
| Session type | Stateless (JWT) | Appropriate for API |
| Token algorithm | HMAC-SHA256 | Secure |
| Token lifetime | 24 hours | Too long — recommend 1-4 hours |
| Token storage (client) | localStorage | Acceptable for SPA; HttpOnly cookie preferred |
| Token transmission | Authorization: Bearer header | Secure |
| SSE fallback | Query parameter token | Acceptable for EventSource limitation |
| CSRF protection | Not needed (stateless, no cookies) | Correct |

### 6.3 Password Security

| Property | Value | Assessment |
|---|---|---|
| Hashing algorithm | BCrypt (10 rounds) | Secure |
| Minimum length | 6 characters | Insufficient — recommend 12+ |
| Complexity requirements | None | Insufficient — recommend character class requirements |
| Password history | Not tracked | Recommend tracking last 5 |
| Account lockout | None | Recommend lockout after 5-10 failed attempts |
| Force password change | After admin reset | Good |
| Self-service change | Requires current password | Good |

---

## 7. Authorization & Access Control

### 7.1 RBAC Assessment

| Role | Capabilities | Properly Enforced |
|---|---|---|
| ADMIN | Full system access, user management, measure deletion | ✅ Yes |
| DEPARTMENT_ADMIN | Department user management, department measures | ✅ Yes |
| USER | Own resource CRUD, FHIR access, CQL execution | ✅ Yes |

### 7.2 Resource-Level Access Control

| Resource | Ownership Check | Admin Bypass | Department Scope |
|---|---|---|---|
| Measures | ✅ ownerUsername | ✅ ADMIN | ✅ DEPT_ADMIN |
| Test Cases | ✅ Via measure ownership | ✅ ADMIN | ✅ DEPT_ADMIN |
| CDS Artifacts | ✅ ownerUsername | ✅ ADMIN | — |
| Notifications | ✅ User-scoped queries | — | — |
| Audit Logs | ❌ ADMIN-only access | ✅ ADMIN | — |
| User Management | ❌ ADMIN endpoints | ✅ ADMIN | ✅ DEPT_ADMIN |

### 7.3 API Endpoint Authorization Matrix

| Endpoint Pattern | Auth Required | Role Required | Test Coverage |
|---|---|---|---|
| `POST /api/auth/*` | No | — | ✅ AuthControllerTest |
| `GET /api/admin/*` | Yes | ADMIN, DEPT_ADMIN | ✅ AdminControllerTest |
| `DELETE /api/measures/*` | Yes | ADMIN | ✅ MeasureControllerTest |
| `GET /api/fhir/*` | Yes | Any authenticated | ✅ FhirControllerTest |
| `GET /cds-services` | No (discovery) | — | ✅ CdsHooksControllerTest |
| `POST /cds-services/*` | No (standard) | — | ✅ CdsHooksControllerTest |
| `GET /actuator/health` | No | — | Infrastructure |

---

## 8. Data Protection

### 8.1 Encryption Assessment

| Layer | Mechanism | Strength | Assessment |
|---|---|---|---|
| Transport | TLS 1.2+ (Nginx) | Strong | HSTS max-age=31536000 |
| At rest (sensitive fields) | AES-256-GCM | Strong | Per-field encryption via JPA converter |
| Password storage | BCrypt (10 rounds) | Strong | Industry standard |
| Token hashing | SHA-256 | Strong | For password reset tokens |
| Key derivation | PBKDF2 (65,536 iterations) | Strong | NIST compliant |

### 8.2 Data Minimization

| Practice | Implementation |
|---|---|
| In-memory PHI processing | FHIR bundles processed in memory during CQL execution, not persisted |
| Date shifting | `DateShiftService` for test case data de-identification |
| Email hashing | SHA-256 hash for lookup, encrypted storage for recovery |
| Audit log sanitization | User-agent truncated to 500 chars; no request body logged |

### 8.3 Data Leakage Prevention

| Control | Implementation |
|---|---|
| Error message sanitization | GlobalExceptionHandler returns safe messages, no stack traces |
| Rate limiting | 60 req/min prevents bulk data extraction |
| Export caps | Audit log export limited to 10,000 records |
| CORS restrictions | Restricted origin allowlist |
| CSP header | `default-src 'self'` prevents data exfiltration to unauthorized domains |

---

## 9. Input Validation & Injection

### 9.1 XSS Protection Assessment

| Layer | Component | Type | Coverage |
|---|---|---|---|
| 1 (Request) | `XssFilter` | HTML entity encoding | All request parameters and headers (except Authorization) |
| 2 (Validation) | `@NoXss` / `NoXssValidator` | Pattern rejection | Annotated fields: `<script>`, `javascript:`, `on*=`, `<iframe>`, `eval()` |
| 3 (Deserialization) | `XssStringDeserializer` | Pattern removal | All JSON string values (global Jackson config) |
| 4 (Rendering) | React JSX | Auto-escaping | All rendered output |
| 5 (Headers) | CSP | Browser enforcement | `script-src 'self' cdn.jsdelivr.net` |

**Assessment**: Multi-layer XSS protection is comprehensive. The `@JsonDeserialize(using = None.class)` bypass for CQL code fields is appropriate — CQL content requires special characters and is not rendered as HTML.

### 9.2 SQL Injection Assessment

| Area | Protection | Status |
|---|---|---|
| Spring Data JPA | Parameterized queries via JPQL/Criteria | ✅ Protected |
| Custom queries | `@Query` annotations with parameter binding | ✅ Protected |
| Dynamic queries | None identified (all via JPA methods) | ✅ N/A |
| Raw SQL | None identified | ✅ N/A |

### 9.3 SSRF Assessment

| Control | Implementation | Status |
|---|---|---|
| URL scheme validation | Only HTTP/HTTPS allowed | ✅ |
| Hostname resolution | Resolved before IP check | ✅ |
| Private IP blocking | Loopback, link-local, site-local, multicast | ✅ |
| Embedded credentials | Rejected (userinfo in URL) | ✅ |
| Redirect following | Not explicitly controlled | ⚠️ Minor |
| Dev mode exception | Localhost allowed in dev profile | ✅ Appropriate |

---

## 10. API Security

### 10.1 API Authentication

| Endpoint Group | Auth Method | Rate Limited |
|---|---|---|
| `/api/auth/*` | None (public) | ✅ Yes |
| `/api/*` (authenticated) | JWT Bearer token | ✅ Yes |
| `/cds-services` (discovery) | None (CDS Hooks spec) | ✅ Yes |
| `/cds-services/{id}` | None (CDS Hooks spec) | ✅ Yes |
| `/cds-services/u/{userId}/*` | API key | ✅ Yes |
| `/actuator/health` | None (health check) | ✅ Yes |

### 10.2 API Rate Limiting

| Property | Value |
|---|---|
| Algorithm | Token bucket (Bucket4j) |
| Rate | 60 requests/minute per IP |
| Headers | X-RateLimit-Limit, X-RateLimit-Remaining |
| Over-limit response | HTTP 429 Too Many Requests |
| Options bypass | OPTIONS requests exempt (CORS preflight) |
| Bucket cleanup | Every 5 minutes |

### 10.3 API Error Handling

| Exception | HTTP Status | Information Disclosed |
|---|---|---|
| `ResourceNotFoundException` | 404 | Resource type + ID (safe) |
| `DuplicateResourceException` | 409 | Field name + value (safe) |
| `ValidationException` | 400 | Validation error details (safe) |
| `AccessDeniedException` | 403 | "Access denied" message |
| `IllegalArgumentException` | 400 | Argument-specific message (safe) |
| Generic `Exception` | 500 | "An unexpected error occurred" (no leak) |

**Assessment**: Error handling is secure. The `GlobalExceptionHandler` sanitizes all error responses and never exposes stack traces, internal paths, or implementation details.

---

## 11. Infrastructure Security

### 11.1 Docker Security

| Control | Implementation | Status |
|---|---|---|
| Non-root user | `appuser` in backend Dockerfile | ✅ |
| Multi-stage build | Builder stage separated from runtime | ✅ |
| Alpine base image | Minimal attack surface | ✅ |
| Resource limits | 2 CPU, 1GB RAM for backend | ✅ |
| Health checks | All services have health checks | ✅ |
| Graceful shutdown | 35-second grace period | ✅ |
| Network isolation | Bridge network (cql-network) | ✅ |

### 11.2 Nginx Security

| Control | Implementation | Status |
|---|---|---|
| Security headers | Full set (HSTS, CSP, X-Frame, etc.) | ✅ |
| Actuator blocking | /actuator → 404 | ✅ |
| Proxy headers | X-Forwarded-For, X-Forwarded-Proto | ✅ |
| Timeouts | Connect 60s, Send 120s, Read 180s | ✅ |
| Gzip | Enabled (min 1000 bytes) | ✅ |
| Cache | 1 year for static assets | ✅ |

### 11.3 Database Security

| Control | Implementation | Status |
|---|---|---|
| Data checksums | PostgreSQL `-k` flag | ✅ |
| Credentials | Environment variables (not in config files) | ✅ |
| SSL mode | `sslmode=prefer` (docker profile) | ✅ |
| Connection pooling | HikariCP (Spring default) | ✅ |
| H2 console (dev only) | Frame options SAMEORIGIN | ⚠️ Accepted |

### 11.4 Monitoring Security

| Control | Implementation | Status |
|---|---|---|
| Grafana sign-up | Disabled | ✅ |
| Prometheus endpoints | health, metrics, prometheus (read-only) | ✅ |
| AlertManager | Integrated for operational alerts | ✅ |
| Metric retention | 30 days | ✅ |

---

## 12. Positive Findings

The following security measures demonstrate strong security engineering:

1. **Defense-in-depth XSS protection**: Three independent layers (request filter, validation annotation, Jackson deserializer) plus React's built-in escaping and CSP headers — industry-leading protection.

2. **Comprehensive audit logging**: Every API request is logged with user identity, action, resource, IP address, and response time. PHI access is tracked separately. This exceeds typical HIPAA audit requirements.

3. **SSRF prevention**: `InputValidator.validateUrl()` resolves hostnames and blocks private IP ranges, preventing SSRF attacks via FHIR server configuration — a common vulnerability in healthcare integrations.

4. **Resilience4j circuit breakers**: FHIR server dependencies are protected with circuit breakers and fallback methods, preventing cascading failures and providing graceful degradation.

5. **Email enumeration prevention**: The forgot-password endpoint returns success regardless of whether the email exists, preventing user enumeration attacks.

6. **Test case date shifting**: `DateShiftService` provides built-in data de-identification for test cases, reducing the risk of PHI exposure in test data.

7. **Secure error handling**: `GlobalExceptionHandler` maps all exceptions to safe HTTP responses without leaking implementation details, stack traces, or internal paths.

8. **Rate limiting with cleanup**: Token bucket rate limiter with automatic bucket cleanup every 5 minutes prevents memory exhaustion from attacker-generated unique IPs.

9. **JWT key validation at startup**: Application fails fast if JWT secret is insufficient, preventing deployment with weak keys.

10. **Non-root Docker user**: Backend runs as `appuser`, limiting container escape impact.

---

## 13. Recommendations

### Priority 1 — Address within 30 days

| # | Recommendation | Effort | Impact |
|---|---|---|---|
| R1 | **Implement MFA** (TOTP/WebAuthn) for ADMIN and DEPARTMENT_ADMIN roles | High | Eliminates single-factor authentication risk |
| R2 | **Reduce JWT lifetime** to 1 hour and implement refresh token rotation | Medium | Limits token theft window from 24h to 1h |
| R3 | **Strengthen password policy** to 12+ characters with complexity requirements | Low | Prevents weak password attacks |

### Priority 2 — Address within 90 days

| # | Recommendation | Effort | Impact |
|---|---|---|---|
| R4 | **Add account lockout** after 5 failed login attempts (with exponential backoff) | Medium | Prevents credential stuffing |
| R5 | **Implement encryption key rotation** with key versioning | Medium | Enables key lifecycle management |
| R6 | **Add automated dependency scanning** (OWASP Dependency-Check or Snyk) to CI | Low | Catches known CVEs early |
| R7 | **Implement token revocation** (Redis-backed blacklist or DB-backed session) | Medium | Enables instant session termination |

### Priority 3 — Address within 180 days

| # | Recommendation | Effort | Impact |
|---|---|---|---|
| R8 | **Add password history tracking** (prevent reuse of last 5 passwords) | Low | Prevents password cycling |
| R9 | **Implement SRI** (Subresource Integrity) for CDN-hosted scripts | Low | Prevents CDN compromise |
| R10 | **Add DNS rebinding protection** (re-verify IP after DNS resolution) | Low | Strengthens SSRF prevention |
| R11 | **Remove localhost from CORS** in production configuration | Low | Tightens CORS policy |
| R12 | **Add request body logging** (sanitized) for sensitive operations | Medium | Improves forensic capability |

---

## 14. Appendices

### Appendix A: Testing Tools Used

| Tool | Purpose |
|---|---|
| Manual code review | Static analysis of security-critical code |
| JUnit 5 + Mockito | Automated security test execution |
| Spring Security Test | MockMvc-based authentication/authorization testing |
| Vitest | Frontend security utility testing |
| Docker inspection | Infrastructure configuration review |

### Appendix B: Test Coverage for Security Components

| Component | Test File | Tests |
|---|---|---|
| GlobalExceptionHandler | `GlobalExceptionHandlerTest.java` | 11 |
| JwtTokenProvider | `JwtTokenProviderTest.java` | Tests present |
| EncryptionConverter | `EncryptionConverterTest.java` | Tests present |
| InputValidator | `InputValidatorTest.java` | Tests present |
| OwnershipVerifier | `OwnershipVerifierTest.java` | 11 |
| RateLimitFilter | `RateLimitFilterTest.java` | Tests present |
| AuthController | `AuthControllerTest.java` | Tests present |
| AdminController | `AdminControllerTest.java`, `AdminControllerErrorTest.java` | Tests present |
| extractApiError | `errorUtils.test.ts` | 8 |

### Appendix C: Compliance Cross-Reference

| Finding | HIPAA | ISO 27001 | OWASP |
|---|---|---|---|
| PT-001 (No MFA) | §164.312(d) | A.8.5 | A07:2021 |
| PT-002 (JWT revocation) | §164.312(a)(1)(iii) | A.8.5 | A07:2021 |
| PT-003 (Password policy) | §164.308(a)(5)(ii)(D) | A.5.17 | A07:2021 |
| PT-004 (Key rotation) | §164.312(a)(2)(iv) | A.8.24 | A02:2021 |
| PT-005 (H2 console) | §164.312(a)(1) | A.8.9 | A05:2021 |
| PT-006 (Static salt) | §164.312(a)(2)(iv) | A.8.24 | A02:2021 |
| PT-007 (CORS localhost) | — | A.8.21 | A05:2021 |
| PT-008 (JWT key length) | §164.312(a)(2)(iv) | A.8.24 | A02:2021 |

### Appendix D: Re-test Schedule

| Finding | Re-test Date | Status |
|---|---|---|
| PT-001 | After MFA implementation | Pending |
| PT-002 | After token revocation implementation | Pending |
| PT-003 | After password policy update | Pending |
| PT-004 | After key rotation implementation | Pending |
| PT-005 | N/A (Accepted) | Closed |
| PT-006 | After salt improvement | Pending |
| PT-007 | N/A (Accepted for dev) | Closed |
| PT-008 | N/A (Already mitigated) | Closed |

---

**End of Report**

*Next assessment recommended: Q3 2026 or after significant architecture changes.*
