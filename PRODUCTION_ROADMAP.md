# CQL Platform Production Roadmap

> Generated: 2026-02-07
> Current State: Prototype (~35% production-ready)
> Target: Healthcare production deployment

---

## Production Readiness Scorecard

| Category | Score | Status |
|----------|-------|--------|
| Security | 5% | CRITICAL - No auth, no encryption |
| Testing | 2% | CRITICAL - Nearly no tests |
| Database | 30% | HIGH RISK - H2 not production-ready |
| Monitoring | 10% | CRITICAL - No metrics, no tracing |
| CI/CD | 0% | CRITICAL - No pipeline |
| Frontend Features | 70% | GOOD - Core features complete |
| Backend Features | 75% | GOOD - Core services implemented |
| CQL Engine | 80% | GOOD - Fully functional |
| CDS Hooks | 60% | MEDIUM - Basic features work |
| Measures | 50% | MEDIUM - Basic evaluation works |
| FHIR Integration | 55% | MEDIUM - Read-only mostly |
| Documentation | 30% | HIGH RISK - README only |
| Compliance | 5% | CRITICAL - Not HIPAA/GDPR compliant |

---

## Phase 1: Security & Data (1-2 months)

> MUST DO before any production use

- [ ] Implement Spring Security with OAuth2/JWT authentication
- [ ] Add SMART on FHIR launch support
- [ ] Migrate H2 to PostgreSQL 14+
- [ ] Add Flyway for database schema migrations
- [ ] Implement audit logging (who/what/when for all PHI access)
- [ ] Add HTTPS/TLS certificates
- [ ] Encrypt PHI at rest and in transit
- [ ] Add rate limiting on all API endpoints
- [ ] Add input validation and sanitization (prevent SQL injection, XSS)
- [ ] Implement RBAC (admin vs user roles)

### Details

**Authentication/Authorization (currently NONE)**
- All endpoints are public, no login mechanism
- No user accounts, no API keys
- No session management or token refresh
- No SMART on FHIR EHR launch capability

**Database (currently H2 file-based)**
- CQL libraries stored in ConcurrentHashMap (lost on restart)
- H2 has no clustering, limited concurrency, no replication
- Using `ddl-auto: update` instead of managed migrations
- No backup/restore tooling
- No data encryption at rest

**Compliance (currently not compliant)**
- No HIPAA audit trails
- PHI can appear in plain text logs
- No consent management (GDPR)
- No data retention policies

---

## Phase 2: Testing & Monitoring (1 month)

> MUST DO before production

- [ ] Write unit tests for all backend services (>70% coverage)
- [ ] Write integration tests with Spring Boot test contexts
- [ ] Write API tests for all controller endpoints (MockMvc/RestAssured)
- [ ] Add frontend component tests (Vitest + React Testing Library)
- [ ] Add E2E tests for critical user flows (Playwright or Cypress)
- [ ] Set up Prometheus + Grafana monitoring
- [ ] Add structured JSON logging (replace console logging)
- [ ] Implement distributed tracing (OpenTelemetry)
- [ ] Add error tracking (Sentry or similar)
- [ ] Set up alerting rules for critical failures

### Details

**Current test coverage: ~0%**
- Backend: 1 test file (CqlTranslationServiceTest.java) - basic only
- Frontend: 0 test files
- No integration, API, or E2E tests
- Services not tested: CdsHooksService, MeasureEvaluationService, FhirDataProviderService, CqlExecutionService

**Current monitoring: minimal**
- Spring Actuator health endpoint exists but no metrics exported
- No Prometheus, no Grafana dashboards
- No structured logging (plain text only)
- No distributed tracing across services

---

## Phase 3: CI/CD & Infrastructure (2 weeks)

> Required for DevOps

- [ ] Create GitHub Actions pipeline (build, test, lint, deploy)
- [ ] Set up Kubernetes manifests or Helm chart
- [ ] Add secrets management (Vault or K8s secrets)
- [ ] Configure reverse proxy / API gateway
- [ ] Set up database backups (daily + point-in-time recovery)
- [ ] Add container resource limits (CPU/memory)
- [ ] Add restart policies for Docker services
- [ ] Remove exposed development ports (8080, 8090)
- [ ] Add dependency vulnerability scanning (Snyk/Dependabot)

### Details

**Current infrastructure**
- Docker Compose with 3 services (backend, frontend, hapi-fhir)
- Health checks configured
- No CI/CD pipeline at all
- No container orchestration
- No TLS, no API gateway
- Secrets in plain text in application.yml
- Backend/HAPI ports exposed directly

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
- **Database**: H2 (file-based) with JPA/Hibernate
- **Infrastructure**: Docker Compose (3 services)

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
| Theme | `frontend/src/theme.ts` |
