# CQL Platform Production Roadmap

> Updated: 2026-02-07
> Current State: Phase 3 Complete (~85% production-ready)
> Target: Healthcare production deployment

---

## Production Readiness Scorecard

| Category | Score | Status |
|----------|-------|--------|
| Security | 85% | GOOD - JWT/RBAC, TLS, encryption, audit, rate limiting, XSS, hardened headers, secrets externalized |
| Testing | 80% | GOOD - 185 backend tests passing, frontend/E2E tests written |
| Database | 80% | GOOD - PostgreSQL + Flyway + encrypted PHI + backup/restore + WAL archiving |
| Monitoring | 75% | GOOD - Prometheus + Grafana + structured logging + tracing + alerts + reverse proxy access |
| CI/CD | 80% | GOOD - GitHub Actions CI/CD, Dependabot, Trivy scanning, GHCR deployment |
| Infrastructure | 80% | GOOD - K8s manifests, resource limits, restart policies, network policies, secrets management |
| Frontend Features | 70% | GOOD - Core features complete |
| Backend Features | 80% | GOOD - Core services + auth implemented |
| CQL Engine | 80% | GOOD - Fully functional |
| CDS Hooks | 60% | MEDIUM - Basic features work |
| Measures | 50% | MEDIUM - Basic evaluation works |
| FHIR Integration | 60% | MEDIUM - SMART config + input validation |
| Documentation | 30% | HIGH RISK - README only |
| Compliance | 30% | HIGH RISK - Audit logging done, needs formal certification |

---

## Phase 1: Security & Data - COMPLETE

> All items implemented

- [x] Implement Spring Security with OAuth2/JWT authentication
- [x] Add SMART on FHIR launch support
- [x] Migrate H2 to PostgreSQL 14+
- [x] Add Flyway for database schema migrations
- [x] Implement audit logging (who/what/when for all PHI access)
- [x] Add HTTPS/TLS certificates
- [x] Encrypt PHI at rest and in transit
- [x] Add rate limiting on all API endpoints
- [x] Add input validation and sanitization (prevent SQL injection, XSS)
- [x] Implement RBAC (admin vs user roles)

### Details

**Authentication/Authorization (IMPLEMENTED)**
- JWT auth with login/register/me endpoints (`AuthController`)
- User entity with BCrypt passwords, ADMIN/USER roles
- Stateless JWT with configurable 24h expiry
- SMART on FHIR `.well-known/smart-configuration` endpoint with EHR launch capabilities

**Database (MIGRATED to PostgreSQL)**
- PostgreSQL 16 with persistent Docker volume (production)
- Flyway versioned migrations (V1-V3): schema, audit log, encrypted columns
- H2 retained as `dev` profile for local development
- `ddl-auto: validate` in production (Flyway manages schema)

**Security Hardening (IMPLEMENTED)**
- AES-256-GCM encryption at rest for PHI fields (email via `EncryptionConverter`)
- TLS/HTTPS configuration ready (keystore config + cert generation script)
- HSTS, X-XSS-Protection, X-Content-Type-Options, Referrer-Policy headers
- Token bucket rate limiting (configurable RPM per client IP)
- XSS sanitization filter on all request parameters/headers
- FHIR resource type whitelist + input validation (`InputValidator`)

**Audit & Compliance (IMPLEMENTED)**
- Full audit logging: who/what/when/where for all API access
- `audit_log` table with username, method, path, resource, status, IP, response time
- Indexed by username, timestamp, resource for efficient querying

---

## Phase 2: Testing & Monitoring - COMPLETE

> All 185 backend tests passing (0 failures, 0 errors)

- [x] Write unit tests for all backend services (>70% coverage)
- [x] Write integration tests with Spring Boot test contexts
- [x] Write API tests for all controller endpoints (MockMvc)
- [x] Add frontend component tests (Vitest + React Testing Library)
- [x] Add E2E tests for critical user flows (Playwright)
- [x] Set up Prometheus + Grafana monitoring
- [x] Add structured JSON logging (replace console logging)
- [x] Implement distributed tracing (OpenTelemetry)
- [x] Add error tracking (AOP-based with Micrometer counters)
- [x] Set up alerting rules for critical failures
- [x] Fix `@MockBean` import in 6 controller test files (Spring Boot 3.2 compatibility)
- [x] Fix Mockito/ByteBuddy Java 23 compatibility (upgraded to Mockito 5.14.2 + ByteBuddy 1.15.10)
- [x] Fix Jackson version for HAPI FHIR 7.0.0 compatibility (upgraded to Jackson 2.17.2)
- [x] Fix AuthController to return 401 on bad credentials (was 500)
- [x] Fix MeasureEvaluationService to return "error" when all patient evaluations fail
- [x] Run full test suite — all 185 tests passing

### Details

**Backend Tests (20 files written)**
- Security: JwtTokenProviderTest, InputValidatorTest, EncryptionConverterTest, RateLimitFilterTest
- Services: CqlTranslationServiceUnitTest, CqlLibraryServiceTest, MeasureEvaluationServiceTest, PrefetchRetrieveProviderTest, CdsHooksServiceTest, FhirDataProviderServiceTest
- Controllers: AuthControllerTest, CqlControllerTest, CdsHooksControllerTest, CdsServiceConfigControllerTest, FhirControllerTest, MeasureControllerTest, SmartConfigControllerTest
- Integration: AuthIntegrationTest, CdsServicePersistenceIntegrationTest
- Test config: `application-test.yml` (H2 in-memory, Flyway disabled, rate limiting off)

**Resolved issues:**
- `@MockBean` import fixed: changed from `o.s.b.t.mock.bean.MockBean` (Spring Boot 3.4+) to `o.s.b.t.mock.mockito.MockBean` (correct for Spring Boot 3.2.0)
- Mockito/ByteBuddy upgraded to 5.14.2/1.15.10 for Java 23 compatibility (inline mock maker)
- Jackson upgraded to 2.17.2 via `jackson-bom.version` property (HAPI FHIR 7.0.0 requires `Separators$Spacing` from Jackson 2.17+)
- Maven Surefire configured with `-XX:+EnableDynamicAgentLoading` for Java 23
- AuthController now properly returns HTTP 401 for bad credentials instead of 500
- MeasureEvaluationService returns "error" status when all patient evaluations fail (previously returned "complete")

**Frontend Tests (17 files written)**
- Infrastructure: vitest.config.ts, setup.ts, test-utils.tsx, MSW mock handlers + server
- Store tests: authSlice, editorSlice, executionSlice
- Component tests: ProtectedRoute, Header, Footer, LoginPage, CqlEditor, ExecutionPanel, CdsPanel, FhirBrowser, MeasurePanel
- Hook tests: useCql, useCdsHooks
- Dependencies added: vitest, @vitest/coverage-v8, jsdom, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, msw

**E2E Tests (Playwright, 4 spec files)**
- auth.spec.ts, cql-editor.spec.ts, cds-hooks.spec.ts, navigation.spec.ts
- Config targets localhost:5173 frontend + localhost:8080 backend

**Monitoring (IMPLEMENTED)**
- Structured JSON logging via Logstash Logback Encoder (prod/docker profiles)
- Human-readable console logging (dev/test profiles)
- `MetricsConfig.java`: 12 custom metric beans (Timer + Counter + ErrorCounter for CQL translation, CQL execution, CDS invocation, measure evaluation)
- All 4 services instrumented with `@Autowired(required=false)` Timer/Counter fields
- `ErrorTrackingConfig.java`: AOP-based exception tracking with MDC context + Micrometer counters
- Prometheus scrape config (`docker/prometheus.yml`)
- 7 Prometheus alert rules (`docker/prometheus-alerts.yml`): high error rate, high latency, CQL/CDS/measure failures, service down, high heap usage
- Grafana with auto-provisioned datasource + pre-built CQL Platform dashboard (16 panels: HTTP metrics, CQL/CDS/measure counters, JVM heap, GC, threads, error rates)
- Docker Compose updated with Prometheus (:9090) and Grafana (:3000) services
- OpenTelemetry tracing bridge configured via Micrometer

---

## Phase 3: CI/CD & Infrastructure - COMPLETE

> All items implemented

- [x] Create GitHub Actions pipeline (build, test, lint, deploy)
- [x] Set up Kubernetes manifests (plain manifests, no Helm)
- [x] Add secrets management (env files + K8s secrets)
- [x] Configure reverse proxy / API gateway (Nginx with Grafana/Prometheus proxies)
- [x] Set up database backups (daily pg_dump + point-in-time WAL recovery)
- [x] Add container resource limits (CPU/memory on all services)
- [x] Add restart policies for Docker services (`unless-stopped`)
- [x] Remove exposed development ports (prod: only port 80, dev overlay re-exposes all)
- [x] Add dependency vulnerability scanning (Dependabot + Trivy)

### Details

**Secrets Management (IMPLEMENTED)**
- `docker/.env.example` — template with placeholder values (committed to git)
- `docker/.env` — real dev values (gitignored via root `.gitignore`)
- `docker-compose.yml` uses `${VAR}` references for all secrets (DB password, JWT secret, encryption key, Grafana password)
- `application.yml` no longer has insecure default fallbacks for JWT_SECRET and ENCRYPTION_KEY
- Dev-only defaults moved to `application-dev.yml` (only active with `dev` Spring profile)

**Docker Compose Hardening (IMPLEMENTED)**
- Base `docker-compose.yml` is production-safe: only frontend exposes port 80
- `docker-compose.dev.yml` overlay re-exposes all ports (5432, 8080, 5173, 8090, 9090, 3000)
- `restart: unless-stopped` on all 6 services
- Resource limits: backend 2CPU/1GB, postgres 1CPU/512MB, hapi-fhir 1CPU/1GB, frontend 0.5CPU/256MB, prometheus 0.5CPU/512MB, grafana 0.5CPU/256MB
- PostgreSQL WAL archiving enabled for point-in-time recovery
- `postgres-backup` volume for backup storage
- Dev: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- Prod: `docker compose up`

**Reverse Proxy Enhancements (IMPLEMENTED)**
- Nginx proxies `/grafana/` to grafana:3000 (with WebSocket upgrade for live dashboards)
- Nginx proxies `/prometheus/` to prometheus:9090
- `/api/health` passes through to actuator/health
- `/actuator/` blocked (returns 404)
- Strengthened headers: X-Frame-Options DENY, HSTS (1 year), Content-Security-Policy, Permissions-Policy, Referrer-Policy
- Grafana configured with `GF_SERVER_ROOT_URL` and `GF_SERVER_SERVE_FROM_SUB_PATH` for sub-path serving

**Database Backups (IMPLEMENTED)**
- `docker/scripts/backup-db.sh` — compressed pg_dump with 30-day retention
- `docker/scripts/restore-db.sh` — restore from backup with safety confirmation
- WAL archiving to `/backups/wal/` for point-in-time recovery

**GitHub Actions CI Pipeline (IMPLEMENTED)**
- `ci.yml` — 5 jobs: backend-test (Maven verify), frontend-lint (ESLint + tsc), frontend-test (vitest coverage), docker-build (BuildKit cache), security-scan (Trivy CRITICAL/HIGH)
- `deploy.yml` — triggered on successful CI on main, builds and pushes to GitHub Container Registry (ghcr.io)
- Concurrency control to cancel in-progress runs on same branch
- Docker layer caching via GitHub Actions cache

**Dependency Vulnerability Scanning (IMPLEMENTED)**
- `dependabot.yml` — 6 ecosystems: Maven, npm (frontend), npm (e2e), Docker (backend), Docker (frontend), GitHub Actions
- Weekly schedule with grouped updates (spring-boot, hapi-fhir, cql-framework, mui, testing)

**Kubernetes Manifests (IMPLEMENTED — 16 files)**
- `k8s/namespace.yml` — `cql-platform` namespace
- `k8s/configmap.yml` — non-secret config (DB URL, FHIR URLs, Spring profile)
- `k8s/secrets.yml` — template with base64 placeholders
- `k8s/postgres/` — StatefulSet (1 replica, 10Gi PVC), ClusterIP Service
- `k8s/backend/` — Deployment (2 replicas, rolling update, startup/liveness/readiness probes), ClusterIP Service
- `k8s/frontend/` — Deployment (2 replicas), ClusterIP Service
- `k8s/hapi-fhir/` — Deployment (1 replica), ClusterIP Service
- `k8s/monitoring/` — Prometheus + Grafana Deployments, Services, ConfigMap
- `k8s/ingress.yml` — Nginx Ingress with TLS, rate limiting, security headers
- `k8s/network-policy.yml` — 6 policies: postgres (backend only), backend (frontend + prometheus + ingress), hapi-fhir (backend only), frontend (ingress only), prometheus (ingress only), grafana (ingress only)

---

## Phase 4: Operational Excellence (1 month)

> Required for reliability

- [ ] Add circuit breakers for FHIR server calls (Resilience4j)
- [ ] Implement request timeouts for CQL execution
- [ ] Add FHIR client connection pooling
- [ ] Implement retry logic with exponential backoff for FHIR calls
- [ ] Conduct load testing (target: 100 concurrent users)
- [ ] Create runbooks for common operational issues
- [ ] Set up on-call rotation and alerting
- [ ] Add graceful shutdown handling
- [ ] Implement request queuing for long-running CQL evaluations

### Details

**Current reliability gaps**
- FHIR server calls can hang indefinitely (no timeout)
- No circuit breaker - cascading failures if FHIR server is down
- New FHIR client connection created per request (no pooling)
- Single failure = request fails (no retry)
- No load testing performed
- Long-running CQL can block all threads

---

## Phase 5: Feature Completeness (2-3 months)

> Nice-to-have for MVP

### CQL Editor
- [ ] Persist CQL libraries to database (replace ConcurrentHashMap)
- [ ] Add IntelliSense autocomplete for CQL keywords, functions, valueSets
- [ ] Add code snippets/templates for common CQL patterns
- [ ] Add CQL debugging (step-through execution)
- [ ] Implement library dependency resolution (includes)
- [ ] Add library import/export (FHIR Library resources)
- [ ] Add library versioning with "latest" support

### FHIR Integration
- [ ] Add ValueSet caching (avoid repeated terminology lookups)
- [ ] Implement FHIR Bulk Data Export ($export)
- [ ] Add FHIR Subscription support
- [ ] Add FHIR batch/transaction operations
- [ ] Add FHIR resource validation (profile-based)
- [ ] Implement patient search by demographics (name, DOB, MRN)
- [ ] Add VSAC (Value Set Authority Center) integration

### CDS Hooks
- [ ] Implement CDS Hook feedback endpoint (currently stubbed)
- [ ] Add Card Actions (create/update/delete FHIR resources)
- [ ] Add System Actions support
- [ ] Implement hook type validation
- [ ] Add service versioning
- [ ] Add service usage analytics and error rate tracking
- [ ] Build service testing sandbox (test without real EHR)

### Quality Measures (eCQM)
- [ ] Create persistent measure repository
- [ ] Support FHIR Measure resource import
- [ ] Implement stratification
- [ ] Add composite measures support
- [ ] Add measure report persistence
- [ ] Add report export (QRDA Cat I/III, PDF, Excel)
- [ ] Implement scheduled/batch measure evaluation
- [ ] Add period-over-period comparison

### Frontend UX
- [ ] Add React Error Boundaries (prevent white screen crashes)
- [ ] Add global toast/notification system
- [ ] Add user preferences (editor settings, theme, default FHIR server)
- [ ] Add recent/favorites for CQL libraries
- [ ] Add client-side input validation on all forms
- [ ] Make backend URL environment-configurable
- [ ] Add inline help and user documentation

---

## Phase 6: Compliance & Certification (3-6 months)

> Required for healthcare production

- [ ] HIPAA compliance audit and certification
- [ ] GDPR compliance implementation (if serving EU)
- [ ] ONC Health IT Certification (if required)
- [ ] Security penetration testing (third-party)
- [ ] Third-party security audit
- [ ] Implement data retention and deletion policies
- [ ] Add consent management
- [ ] Create compliance documentation

---

## Architecture Notes

### Current Stack
- **Frontend**: React + TypeScript + MUI + Monaco Editor + Redux + React Query
- **Backend**: Java Spring Boot 3.2.0 + HAPI FHIR 7.0.0 + CQL Engine
- **Database**: PostgreSQL 16 (production) / H2 (dev profile) with JPA/Hibernate + Flyway
- **Infrastructure**: Docker Compose (6 services) + Kubernetes manifests + GitHub Actions CI/CD + Nginx reverse proxy

### Key Files
| Component | Path |
|-----------|------|
| CQL Editor | `frontend/src/components/editor/CqlEditor.tsx` |
| CQL Syntax | `frontend/src/utils/cqlSyntax.ts` |
| Execution Panel | `frontend/src/components/execution/ExecutionPanel.tsx` |
| CDS Panel | `frontend/src/components/cds/CdsPanel.tsx` |
| Measure Panel | `frontend/src/components/measure/MeasurePanel.tsx` |
| FHIR Browser | `frontend/src/components/fhir/FhirBrowser.tsx` |
| CQL Controller | `backend/src/main/java/com/cqlplatform/controller/CqlController.java` |
| CQL Execution | `backend/src/main/java/com/cqlplatform/service/cql/CqlExecutionService.java` |
| CQL Translation | `backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java` |
| CDS Service | `backend/src/main/java/com/cqlplatform/service/cds/CdsHooksService.java` |
| Measure Service | `backend/src/main/java/com/cqlplatform/service/measure/MeasureEvaluationService.java` |
| FHIR Provider | `backend/src/main/java/com/cqlplatform/service/fhir/FhirDataProviderService.java` |
| Docker Compose | `docker/docker-compose.yml` |
| Docker Dev Overlay | `docker/docker-compose.dev.yml` |
| Nginx Config | `docker/nginx.conf` |
| CI Pipeline | `.github/workflows/ci.yml` |
| CD Pipeline | `.github/workflows/deploy.yml` |
| K8s Manifests | `k8s/` |
| DB Backup Script | `docker/scripts/backup-db.sh` |
| Theme | `frontend/src/theme.ts` |
