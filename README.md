# CQL Platform

A comprehensive Clinical Quality Language (CQL) development platform featuring CQL editing, translation, execution, CDS Hooks integration, quality measure evaluation, and FHIR resource browsing.

## Features

### CQL Editor
- Monaco-based editor with CQL syntax highlighting, IntelliSense auto-completion, and code snippets
- Real-time CQL-to-ELM translation with error/warning markers
- CQL execution against FHIR servers with debug tracing
- Library dependency resolution, versioning, and FHIR Library import/export
- Smart paste sanitization (strips smart quotes, zero-width chars from LLM outputs)

### CDS Hooks
- Clinical Decision Support Hooks integration with service discovery and invocation
- Service management: create, edit, version, rollback, enable/disable
- Feedback system: accept/override with reason tracking
- Card and system action rendering with suggestion support
- Usage analytics with invocation counts, error rates, and response times
- Testing sandbox for offline service development

### Quality Measures (eCQM)
- Measure definition repository with CRUD and FHIR Measure import/export
- Population-based evaluation with stratification support
- Composite measures (opportunity and linear scoring)
- Report persistence with export (FHIR MeasureReport, CSV, Excel)
- Scheduled/batch evaluation with cron expressions
- Period-over-period comparison and trend analysis

### FHIR Integration
- Browse and search FHIR resources on any R4 server
- Patient demographics search, resource validation, batch/transaction operations
- Bulk Data Export ($export) with async polling
- VSAC (Value Set Authority Center) integration for terminology
- ValueSet caching with per-cache TTL and admin eviction
- Circuit breakers and retry with exponential backoff on all FHIR calls

### Frontend UX
- Dark mode with persistent user preferences (editor font size, tab size, word wrap, minimap)
- React Error Boundaries preventing white-screen crashes with isolated per-page recovery
- Global toast notification system replacing per-component snackbars
- Recent/favorites sidebar for quick library access
- Client-side input validation on all forms (login, CDS, measures, FHIR)
- Inline help tooltips and quick-start guide drawer
- Configurable backend URLs via environment variables

### Security & Operations
- JWT authentication with RBAC (admin/user roles)
- AES-256-GCM encryption at rest, TLS in transit, hardened HTTP headers
- Audit logging of all API access with full context
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
│       ├── entity/             # JPA entities
│       ├── repository/         # Spring Data repositories
│       ├── service/
│       │   ├── cql/            # CQL translation, execution, library management
│       │   ├── fhir/           # FHIR data provider, validation, bulk export, VSAC
│       │   ├── cds/            # CDS Hooks service, analytics, feedback
│       │   └── measure/        # eCQM evaluation, reports, scheduling, comparison
│       ├── model/              # DTOs and request/response models
│       └── exception/          # Global exception handling
│
├── frontend/                   # React + TypeScript frontend
│   ├── package.json
│   ├── .env.example            # Environment variable template
│   └── src/
│       ├── components/
│       │   ├── common/         # ErrorBoundary, GlobalNotification, PreferencesDialog, HelpTooltip, HelpDrawer
│       │   ├── editor/         # CqlEditor, ElmViewer, LibraryQuickAccess
│       │   ├── execution/      # ExecutionPanel, DebugPanel
│       │   ├── cds/            # CdsPanel (invoke, manage, analytics, sandbox)
│       │   ├── measure/        # MeasurePanel, MeasureLibrary, MeasureReportHistory, MeasureScheduleManager, MeasureComparison
│       │   ├── fhir/           # FhirBrowser
│       │   ├── terminology/    # TerminologyBrowser
│       │   ├── layout/         # Header, Footer
│       │   └── auth/           # ProtectedRoute
│       ├── contexts/           # NotificationContext, PreferencesContext, LibraryHistoryContext
│       ├── hooks/              # useCql, useCdsHooks, useNotification, usePreferences, useLibraryHistory
│       ├── pages/              # EditorPage, CdsPage, MeasuresPage, FhirPage, TerminologyPage, LoginPage
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
├── docs/runbooks/              # Operational runbooks
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

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/login` | POST | JWT authentication |
| `/api/auth/register` | POST | User registration |
| `/api/cql/translate` | POST | Translate CQL to ELM |
| `/api/cql/validate` | POST | Validate CQL syntax |
| `/api/cql/execute` | POST | Execute CQL (with optional debug mode) |
| `/api/cql/libraries` | GET/POST | Manage CQL libraries |
| `/api/cql/libraries/metadata` | GET | Library metadata for IntelliSense |
| `/api/cql/libraries/{id}/fhir` | GET | Export as FHIR Library |
| `/api/cql/libraries/import/fhir` | POST | Import FHIR Library |
| `/cds-services` | GET | CDS service discovery |
| `/cds-services/{id}` | POST | Invoke CDS service |
| `/cds-services/{id}/feedback` | POST | Submit CDS feedback |
| `/cds-services/{id}/sandbox` | POST | Sandbox invocation |
| `/api/cds/services` | GET/POST/PUT/DELETE | Manage CDS service configs |
| `/api/cds/services/analytics` | GET | Service usage analytics |
| `/api/measures` | GET/POST/PUT/DELETE | Manage measure definitions |
| `/api/measures/{id}/$evaluate-measure` | POST | Evaluate quality measure |
| `/api/measures/reports` | GET | Measure report history |
| `/api/measures/reports/{id}/export` | GET | Export report (fhir/csv/excel) |
| `/api/measures/{id}/schedules` | GET/POST | Manage evaluation schedules |
| `/api/measures/compare` | GET | Period-over-period comparison |
| `/api/measures/trend` | GET | Measure trend data |
| `/api/fhir/{resourceType}` | GET | Search FHIR resources |
| `/api/fhir/$validate` | POST | Validate FHIR resource |
| `/api/fhir/$export` | POST | Bulk Data Export kick-off |
| `/api/fhir/vsac/ValueSet/{oid}` | GET | VSAC ValueSet lookup |
| `/api/fhir/cache/stats` | GET | Cache statistics |

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
# Backend (240+ tests)
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
