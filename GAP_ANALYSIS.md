# Gap Analysis: From Prototype to Production

## Executive Summary
The CQL Platform is currently in a **Prototype/Proof-of-Concept** state. While the core logic (CQL execution, CDS Hooks, basic UI) is functional, it lacks critical infrastructure, software security, and operational features required for a "real landing" (Production).

## Missing Critical Features

### 1. Security & Compliance (High Priority)
- [ ] **Authentication/Authorization**: Currently, there is **NO** security. Anyone can access the API.
    - *Recommendation*: Implement OAuth2 / OpenID Connect (OIDC).
    - *Healthcare Standard*: Implement **SMART on FHIR** launch contexts if integration with EHRs is implied.
- [ ] **Audit Logging**: Need to track *who* accessed *what* patient data for HIPAA/GDPR compliance.
- [ ] **Sensitive Data Protection**: Ensure no PHI is logged in plain text (current logs might be verbose).

### 2. Infrastructure & Persistence (High Priority)
- [ ] **Database**: Currently using **H2 (In-Memory)**. Data is lost on restart (unless file-based, which is not scalable).
    - *Recommendation*: Migrate to **PostgreSQL** or **MySQL**.
    - *Versioning*: Add **Flyway** or **Liquibase** for database schema migrations.
- [ ] **Containerization**: `Dockerfile` exists but looks basic. Need a production-grade multi-stage build.
    - *Orchestration*: No `docker-compose.yml` for running full stack (Backend + DB + Frontend + Nginx).

### 3. Frontend Maturity (Medium Priority)
- [ ] **Authentication Integration**: Frontend does not handle login/logout.
- [ ] **Error Handling**: Needs robust error boundaries and user feedback (toasts/alerts) for API failures.
- [ ] **Configuration**: Hardcoded API URLs need to be environment-variable driven.

### 4. Observability (Medium Priority)
- [ ] **Structured Logging**: Move from simple console logs to JSON logs (for ELK/Splunk).
- [ ] **Metrics**: Enable Prometheus metrics (Spring Actuator is there, need to expose/scrape it).
- [ ] **Tracing**: Add OpenTelemetry for tracing requests across microservices (if applicable).

## Roadmap to Production

### Phase 1: Security & Persistence (Next Steps)
1. Replace H2 with PostgreSQL.
2. Implement Spring Security with OAuth2 Resource Server.
3. Add Database Migration tool (Flyway).

### Phase 2: Operations
1. Create production Docker Compose.
2. Configure Nginx as a reverse proxy/gateway.
3. Set up CI/CD pipeline (GitHub Actions).

### Phase 3: EHR Integration
1. Implement SMART on FHIR Launch sequence.
2. Test with Epic/Cerner sandboxes.
