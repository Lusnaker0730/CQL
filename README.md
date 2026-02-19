# CQL Platform

A comprehensive Clinical Quality Language (CQL) development platform featuring CQL editing, translation, execution, CDS Hooks integration, electronic quality measure (eCQM) management, FHIR resource browsing, and an admin dashboard with audit logging.

## Features

### CQL Editor
- Monaco-based editor with CQL syntax highlighting, IntelliSense auto-completion, and code snippets
- Real-time CQL-to-ELM translation with error/warning markers
- CQL execution against FHIR servers with debug tracing
- Library dependency resolution, versioning, and FHIR Library import/export
- Smart paste sanitization (strips smart quotes, zero-width chars from LLM outputs)

### CQL Builder Panel
- Visual CQL construction panel alongside the editor
- Sections for includes, value sets, codes, parameters, definitions, and functions
- Terminology search and code lookup integration
- One-click CQL snippet generation and cursor insertion

### CDS Hooks
- Clinical Decision Support Hooks integration with service discovery and invocation
- Service management: create, edit, version, rollback, enable/disable
- Strategy-based card generation: CQL Tuple cards (default) and FHIR PlanDefinition actions
- PlanDefinition support: condition evaluation, priority mapping, dynamic values, sub-action suggestions
- Feedback system: accept/override with reason tracking
- Card and system action rendering with suggestion support
- Usage analytics with invocation counts, error rates, and response times
- Testing sandbox for offline service development

### Quality Measures (eCQM)
- Two-panel layout: measure library browser + tabbed measure editor (Details, CQL, Population Criteria, Evaluate, Test Cases, Reports)
- Population-based evaluation with stratification and risk adjustment support
- Composite measures (opportunity and linear scoring)
- Measure versioning with history, sharing, ownership transfer, and access control
- Workflow lifecycle: draft → submit for review → approve/reject → retire
- Measure locking to prevent concurrent edits
- Measure validation (full and quick) with care setting classification
- Report persistence with export (FHIR MeasureReport, CSV, Excel, HQMF)
- Scheduled/batch evaluation with cron expressions
- Period-over-period comparison, trend analysis, and dashboard view
- Audit trail per measure

### Test Cases
- Test case CRUD with expected vs actual population comparison
- Visual Bundle Builder: form-based FHIR resource construction with 15+ resource types
- FHIR structure definition introspection for auto-generating input forms
- Bidirectional sync between visual builder and raw JSON editor
- Test case execution with coverage tracking
- Reference auto-linking between resources in a bundle

### FHIR Integration
- Browse and search FHIR resources on any R4 server
- Patient demographics search, resource validation, batch/transaction operations
- Bulk Data Export ($export) with async polling
- VSAC (Value Set Authority Center) integration for terminology
- Terminology operations: ValueSet expand, CodeSystem validate-code, lookup, search
- Implementation Guide support: packages, profiles, value sets, code systems
- FHIR structure definition introspection for resource metadata
- ValueSet caching with per-cache TTL and admin eviction
- Circuit breakers and retry with exponential backoff on all FHIR calls

### Administration
- User management: create users, assign roles, enable/disable accounts
- Password reset: self-service email flow and admin-initiated reset
- Audit dashboard with log search, filtering, and CSV export
- Audit statistics: daily activity, top users, action breakdowns
- Compliance views: PHI access tracking, login activity, security events

### Frontend UX
- Dark mode with persistent user preferences (editor font size, tab size, word wrap, minimap)
- Design system: GradientButton, StatusChip, SectionHeader, WorkflowIndicator
- Lazy loading with skeleton screens (CardListSkeleton, DashboardSkeleton, TableSkeleton)
- React Error Boundaries preventing white-screen crashes with isolated per-page recovery
- Unsaved changes guard on measure editor tabs
- Global toast notification system
- Recent/favorites sidebar for quick library access
- Client-side input validation with DTO validation and JSON body XSS filtering
- Inline help tooltips and quick-start guide drawer
- Configurable backend URLs via environment variables

### Security & Operations
- JWT authentication with RBAC (admin/user roles)
- AES-256-GCM encryption at rest, TLS in transit, hardened HTTP headers
- Audit logging of all API access with full context and retention policy
- Rate limiting, XSS sanitization, FHIR resource type whitelisting
- Prometheus + Grafana monitoring with custom CQL/CDS/measure metrics
- Resilience4j circuit breakers, connection pooling, execution thread pool with queuing
- Kubernetes manifests, network policies, and GitHub Actions CI/CD

## Project Structure

```
├── backend/                    # Java Spring Boot backend
│   ├── pom.xml
│   └── src/main/java/com/cqlplatform/
│       ├── config/             # Spring configuration (security, async, metrics, resilience)
│       ├── controller/         # REST API controllers
│       │   ├── AuthController          # Login, register, password reset
│       │   ├── CqlController           # CQL translate, validate, execute, libraries
│       │   ├── CdsHooksController      # CDS service discovery and invocation
│       │   ├── CdsServiceConfigController  # CDS service management
│       │   ├── MeasureController       # Measures, test cases, reports, schedules
│       │   ├── FhirController          # FHIR resources, terminology, structure defs
│       │   ├── AdminController         # User management
│       │   └── AuditController         # Audit dashboard
│       ├── entity/             # JPA entities
│       ├── repository/         # Spring Data repositories
│       ├── service/
│       │   ├── cql/            # CQL translation, execution, library management
│       │   ├── fhir/           # FHIR data provider, validation, bulk export, VSAC, structure defs
│       │   ├── cds/            # CDS Hooks: CRUD, invocation, card strategies, analytics, feedback
│       │   └── measure/        # eCQM evaluation, reports, scheduling, comparison
│       ├── model/              # DTOs and request/response models
│       │   ├── auth/           # Auth and admin DTOs
│       │   ├── audit/          # Audit log DTOs
│       │   └── fhir/           # FHIR structure definition DTOs
│       └── exception/          # Global exception handling
│
├── frontend/                   # React + TypeScript frontend
│   ├── package.json
│   ├── .env.example            # Environment variable template
│   └── src/
│       ├── components/
│       │   ├── common/         # GradientButton, StatusChip, SectionHeader, ErrorBoundary, Skeletons
│       │   ├── editor/         # CqlEditor, ElmViewer, LibraryQuickAccess
│       │   ├── execution/      # ExecutionPanel, DebugPanel
│       │   ├── builder/        # CqlBuilderPanel, IncludesSection, ValueSetSection, CodesSection, etc.
│       │   ├── cds/            # CdsPanel (invoke, manage, analytics, sandbox)
│       │   ├── measure/        # MeasureEditor, MeasureLibrary, WorkflowIndicator, PopulationCriteriaTab, etc.
│       │   ├── testcase-builder/  # VisualBundleBuilder, ResourceForm, ElementField, FHIR field components
│       │   ├── fhir/           # FhirBrowser
│       │   ├── terminology/    # TerminologyBrowser
│       │   ├── layout/         # Header, Footer
│       │   └── auth/           # ProtectedRoute
│       ├── contexts/           # NotificationContext, PreferencesContext, LibraryHistoryContext, BundleBuilderContext
│       ├── hooks/              # useCql, useCdsHooks, useMeasures, useCqlStructure, useFhirMetadata, useUnsavedChangesGuard, etc.
│       ├── pages/              # EditorPage, CdsPage, MeasuresPage, FhirPage, TerminologyPage, AdminUsersPage, AuditDashboardPage, LoginPage
│       ├── api/                # Axios API clients (configurable base URLs)
│       ├── store/              # Redux slices (auth, editor, execution)
│       ├── utils/              # CQL syntax, validation utilities
│       ├── constants/          # Help content
│       ├── theme.ts            # MUI theme with light/dark mode support
│       └── types/              # TypeScript interfaces
│
├── docker/                     # Docker configuration
│   ├── docker-compose.yml      # Production (only port 80 exposed)
│   ├── docker-compose.dev.yml  # Dev overlay (all ports exposed)
│   ├── .env.example            # Secrets template
│   ├── nginx.conf              # Reverse proxy with Grafana/Prometheus
│   ├── alertmanager.yml        # Alert routing
│   ├── prometheus.yml          # Metrics scraping
│   ├── prometheus-alerts.yml   # Alert rules
│   └── scripts/                # backup-db.sh, restore-db.sh
│
├── k8s/                        # Kubernetes manifests (16 files)
├── load-tests/                 # k6 load testing scripts
├── docs/                       # Admin guide, deployment guide, runbooks
└── .github/workflows/          # CI/CD pipelines
```

## Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+
- Docker & Docker Compose (optional)

## Quick Start

### Backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts at `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts at `http://localhost:5173`.

### Docker (Production)

```bash
cd docker
cp .env.example .env  # Edit with real secrets
docker compose up -d
```

### Docker (Development)

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

This starts all services with exposed ports:
- Frontend at `http://localhost:80` (Nginx)
- Backend at `http://localhost:8080`
- HAPI FHIR Server at `http://localhost:8090/fhir`
- PostgreSQL at `localhost:5432`
- Prometheus at `http://localhost:9090`
- Grafana at `http://localhost:3000`

## API Endpoints

### Authentication

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/login` | POST | JWT authentication |
| `/api/auth/register` | POST | User registration |
| `/api/auth/me` | GET | Current user info |
| `/api/auth/forgot-password` | POST | Request password reset email |
| `/api/auth/reset-password` | POST | Reset password with token |
| `/api/auth/change-password` | POST | Change password (authenticated) |

### CQL

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/cql/translate` | POST | Translate CQL to ELM |
| `/api/cql/validate` | POST | Validate CQL syntax |
| `/api/cql/execute` | POST | Execute CQL (with optional debug mode) |
| `/api/cql/libraries` | GET/POST | Manage CQL libraries |
| `/api/cql/libraries/metadata` | GET | Library metadata for IntelliSense |
| `/api/cql/libraries/{id}/fhir` | GET | Export as FHIR Library |
| `/api/cql/libraries/import/fhir` | POST | Import FHIR Library |

### CDS Hooks

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/cds-services` | GET | CDS service discovery |
| `/cds-services/{id}` | POST | Invoke CDS service |
| `/cds-services/{id}/feedback` | POST | Submit CDS feedback |
| `/cds-services/{id}/sandbox` | POST | Sandbox invocation |
| `/api/cds/services` | GET/POST/PUT/DELETE | Manage CDS service configs |
| `/api/cds/services/analytics` | GET | Service usage analytics |

### Quality Measures

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/measures` | GET/POST/PUT/DELETE | Manage measure definitions |
| `/api/measures/{id}/$evaluate-measure` | POST | Evaluate quality measure |
| `/api/measures/{id}/cql-expressions` | GET | List CQL expressions for a measure |
| `/api/measures/{id}/test-cases` | GET/POST | Test case CRUD |
| `/api/measures/{id}/test-cases/{id}/run` | POST | Run single test case |
| `/api/measures/{id}/test-cases/run` | POST | Run all test cases |
| `/api/measures/{id}/version` | POST | Create new measure version |
| `/api/measures/{id}/history` | GET | Version history |
| `/api/measures/{id}/share` | POST | Share measure with user |
| `/api/measures/{id}/lock` | POST | Lock measure for editing |
| `/api/measures/{id}/unlock` | POST | Unlock measure |
| `/api/measures/{id}/submit-for-review` | POST | Submit for review |
| `/api/measures/{id}/approve` | POST | Approve measure |
| `/api/measures/{id}/reject` | POST | Reject measure |
| `/api/measures/{id}/retire` | POST | Retire measure |
| `/api/measures/{id}/validate` | POST | Full measure validation |
| `/api/measures/{id}/validate/quick` | POST | Quick validation |
| `/api/measures/batch-evaluate` | POST | Batch evaluation |
| `/api/measures/dashboard` | GET | Measure dashboard summary |
| `/api/measures/reports` | GET | Measure report history |
| `/api/measures/reports/{id}/export` | GET | Export report (fhir/csv/excel) |
| `/api/measures/{id}/schedules` | GET/POST | Manage evaluation schedules |
| `/api/measures/compare` | GET | Period-over-period comparison |
| `/api/measures/trend` | GET | Measure trend data |

### FHIR

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/fhir/{resourceType}` | GET | Search FHIR resources |
| `/api/fhir/{resourceType}/{id}` | GET | Read single resource |
| `/api/fhir/{resourceType}` | POST | Create resource |
| `/api/fhir/{resourceType}/{id}` | PUT | Update resource |
| `/api/fhir/{resourceType}/{id}` | DELETE | Delete resource |
| `/api/fhir/$validate` | POST | Validate FHIR resource |
| `/api/fhir/Bundle/$transaction` | POST | Batch/transaction bundle |
| `/api/fhir/$export` | POST | Bulk Data Export kick-off |
| `/api/fhir/$export-status` | GET | Export status polling |
| `/api/fhir/Patient/$search-by-demographics` | GET | Demographics search |
| `/api/fhir/vsac/ValueSet/{oid}` | GET | VSAC ValueSet lookup |
| `/api/fhir/vsac/ValueSet/{oid}/$expand` | GET | Expand VSAC ValueSet |
| `/api/fhir/ValueSet/$expand` | GET | Expand ValueSet |
| `/api/fhir/CodeSystem/$validate-code` | GET | Validate code in CodeSystem |
| `/api/fhir/CodeSystem/$lookup` | GET | Code lookup |
| `/api/fhir/CodeSystem/$search-codes` | GET | Search codes in CodeSystem |
| `/api/fhir/structure-definitions/resource-types` | GET | Supported resource types |
| `/api/fhir/structure-definitions/{resourceType}` | GET | Resource element metadata |
| `/api/fhir/ig/packages` | GET | Implementation Guide packages |
| `/api/fhir/ig/profiles` | GET | IG profiles |
| `/api/fhir/cache/stats` | GET | Cache statistics |

### Administration (Admin only)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/users` | GET | List all users |
| `/api/admin/users` | POST | Create new user |
| `/api/admin/users/{id}/role` | PUT | Update user role |
| `/api/admin/users/{id}/enabled` | PUT | Enable/disable user |
| `/api/admin/users/{id}/reset-password` | POST | Admin reset user password |
| `/api/admin/audit/logs` | GET | Search audit logs |
| `/api/admin/audit/logs/export` | GET | Export audit logs (CSV) |
| `/api/admin/audit/stats` | GET | Audit statistics summary |
| `/api/admin/audit/phi-access` | GET | PHI access events |
| `/api/admin/audit/login-activity` | GET | Login activity |
| `/api/admin/audit/security-events` | GET | Security events (401/403) |

## Configuration

### Backend (application.yml)

```yaml
fhir:
  server:
    url: http://hapi.fhir.org/baseR4
  terminology:
    url: http://tx.fhir.org/r4

cql:
  translation:
    enable-annotations: true
    enable-locators: true
  execution:
    timeout-seconds: 30

audit:
  retention-days: 365
```

### Frontend Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_URL` | Backend API base URL | `/api` |
| `VITE_CDS_URL` | CDS Services base URL | `/cds-services` |

Copy `frontend/.env.example` to `frontend/.env` and customize as needed.

### Backend Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `FHIR_SERVER_URL` | FHIR server URL | `http://hapi.fhir.org/baseR4` |
| `FHIR_TERMINOLOGY_URL` | Terminology server URL | `http://tx.fhir.org/r4` |
| `JWT_SECRET` | JWT signing secret | (required in production) |
| `ENCRYPTION_KEY` | AES-256 encryption key | (required in production) |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/cqlplatform` |
| `VSAC_API_KEY` | VSAC API key for terminology | (optional) |
| `AUDIT_RETENTION_DAYS` | Audit log retention period | `365` |

## Key Dependencies

### Backend
- Spring Boot 3.2
- CQL Framework (cql-to-elm, engine) 3.29.0
- HAPI FHIR 7.0.0
- CQF Clinical Reasoning 3.0.0
- Resilience4j 2.2.0
- PostgreSQL 16 / H2 (dev)
- Flyway migrations
- Apache POI (Excel export)

### Frontend
- React 18 + TypeScript
- Monaco Editor (@monaco-editor/react)
- Material-UI 5
- Redux Toolkit
- TanStack Query (React Query)
- Axios
- React Router 6

## Example CQL

```cql
library DiabetesManagement version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1'

valueset "Diabetes": 'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001'

context Patient

define "Has Diabetes":
  exists [Condition: "Diabetes"] C
    where C.clinicalStatus ~ 'active'
```

## Development

### Running Tests

```bash
# Backend (300+ tests)
cd backend
mvn test

# Frontend (Vitest + React Testing Library)
cd frontend
npm test

# E2E (Playwright)
cd e2e
npx playwright test
```

### Building for Production

```bash
# Backend
cd backend
mvn package -DskipTests

# Frontend
cd frontend
npm run build
```

### Keyboard Shortcuts (CQL Editor)

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Translate CQL |
| Ctrl+Enter | Execute CQL |

## License

MIT License

## References

- [CQL Specification](https://cql.hl7.org/)
- [CDS Hooks](https://cds-hooks.org/)
- [FHIR](https://www.hl7.org/fhir/)
- [HAPI FHIR](https://hapifhir.io/)
