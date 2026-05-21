# CQL Platform

> **Version 2.5.0** | Updated 2026-05-04

A comprehensive Clinical Quality Language (CQL) development platform featuring CQL editing, translation, execution, CDS Hooks integration, CDS authoring, eCQM visual authoring with CQL generation, electronic quality measure (eCQM) evaluation, quality measure dashboards, FHIR resource browsing, EHR integration (SMART Backend Services), TW Core synthetic patient generation, AI-assisted CQL repair, an interactive Learn Center, internationalization (i18n), and an admin dashboard with audit logging. The platform is developed under an IEC 62304 / ISO 14971 / TFDA workflow with automated regulatory document generation.

## Features

### CQL Editor
- Monaco-based editor with CQL syntax highlighting, IntelliSense auto-completion, and code snippets
- Real-time CQL-to-ELM translation with error/warning markers
- CQL execution against FHIR servers with enhanced debug tracing (source locators, expression dependencies)
- Library dependency resolution, versioning, and FHIR Library import/export
- Library dependency analysis with version conflict detection and impact visualization
- Undo/redo support for builder operations with Redux history stack
- Smart paste sanitization (strips smart quotes, zero-width chars from LLM outputs)

### CQL Builder Panel
- Visual CQL construction panel alongside the editor
- Sections for includes, value sets, codes, concepts, parameters, definitions, and functions
- Retrieve Builder: guided FHIR retrieve expression construction with C3F modifiers
- Query Builder: visual WHERE/SORT/RETURN clause construction with field auto-complete
- CDS Card Builder: CQL Tuple card generation with literal/expression field modes
- Operator Panel: expression builder and operator reference
- Syntax-highlighted CQL preview (Monaco `colorize()`) in all builder components
- Duplicate name detection with confirmation dialog before snippet insertion
- Terminology search, VSAC lookup, and TWCore browse integration
- One-click CQL snippet generation and cursor insertion

### CDS Authoring Tool
- Visual artifact authoring with 11-tab workspace: Inclusions, Exclusions, Subpopulations, Base Elements, Recommendations, Parameters, Error Handling, External CQL, Review CQL, Testing, Summary
- Drag-and-drop element tree with conjunction groups (AND/OR) and nested conditions
- Template-based element selection with form-driven configuration
- Modifier system: age range, gender, look back, most recent, active/confirmed, etc.
- Automatic CQL generation from visual artifact tree (R4 FHIR only)
- Save-time expression tree validation: unknown template/modifier ID rejection with 400 error details
- Duplicate `define` name detection: same-category duplicates, cross-category collisions, reserved system name conflicts
- CQL three-valued logic correctness: empty exclusion produces `false` instead of `null`
- External CQL library upload, parsing, and integration
- Artifact testing against FHIR patient data with result visualization
- One-click deployment as CDS Hooks service
- Save artifact as reusable CQL library
- CQL import: parse existing CQL into visual artifact structure
- Query builder for FHIR resource queries
- TWCore catalog integration for Taiwan-specific code systems
- CPG metadata editing for Clinical Practice Guideline compliance
- Artifact duplication and version management

### eCQM Authoring Tool
- Visual eCQM artifact authoring with 8-tab workspace: Summary, Population Groups, Base Elements, Parameters, Supplemental Data, Stratifiers, External CQL, Review CQL
- Four scoring types: Proportion, Ratio, Continuous Variable, Cohort (per CMS 2026 eCQM Logic and Implementation Guidance v9.0)
- Drag-and-drop expression tree builder for each population criterion (reuses CDS ConjunctionGroup engine)
- Automatic eCQM CQL generation with FHIR R4 retrieve patterns and `Measurement Period` parameter
- Ratio dual Initial Population support: separate IP for denominator and numerator paths
- Episode-based measures: population basis selection (Patient/Encounter/Procedure/MedicationRequest/Observation)
- Continuous Variable observation functions with configurable aggregate methods (Count, Sum, Average, Median, Max, Min, Percentile)
- Multi-group support with automatic define name suffixes to prevent collisions
- Supplemental Data Elements: standard CDC SDEs (Ethnicity, Race, Sex, Payer) with checkbox toggle + custom SDE expression trees
- Stratifier expression trees per population group (auto-disabled for ratio dual-IP per CMS rules)
- One-click publish to MeasureDefinition for evaluation pipeline integration
- CQL preview with ELM translation and validation
- Expression tree validation: XSS filtering, template/modifier ID verification, required population checks
- Debounced auto-save with optimistic UI updates

### Quality Measure Dashboard
- Real-time dashboard for quality measure performance with department-level drilldown
- Score trend chart: multi-line time series with target/warning threshold reference lines
- Threshold alert panel: scoring-family-aware formatting (proportion `%`, continuous-variable `unit`, cohort integer count)
- Score distribution chart and department average heatmap
- Quality report panel with measure preview table and CSV/Excel export
- Filter bar: period selector, scoring type, department scope
- Per-measure thresholds (target / warning / critical) with `MeasureThresholdEntity` persistence

### EHR Integration
- SMART Backend Services authentication (asymmetric JWT client assertion, PKI-based)
- TLS context factory for mutual TLS connections to hospital EHR FHIR servers
- Connection health checks with circuit breaker and exponential backoff retry
- Async patient import with progress tracking and resumable failure retry
- Batch import jobs with `BatchImportJobEntity` lifecycle and `FailedImportEntity` retry queue
- FHIR Subscription registration and event handling
- Patient match/search across multiple EHR endpoints
- EHR outage banner driven by `EhrOutageContext` with graceful degradation

### TW Core Synthetic Patient Generator
- Pure-frontend FHIR R4 patient bundle generator targeting TW Core IG profiles
- Taiwan demographics (name, address, national ID with checksum, phone) — `twDemographics.ts`
- Clinical data driven by JSON config files (`config/twcore/`) — conditions/observations/medications/allergies/scenarios — no code changes needed to add new clinical patterns
- Bundle upload via `PUT ResourceType/id` to preserve client-side IDs (HAPI compatible)
- Configurable scenarios for diabetes, hypertension, CKD, and custom multi-morbidity profiles

### AI-Assisted CQL Repair
- Cloud AI provider (configurable) and local Ollama provider for CQL error fix suggestions
- Curated CQL knowledge base (`CqlKnowledgeBase` + `KnowledgeEntry`) seeds the prompt with domain-specific guidance
- `CqlFixService` integrates translator error/warning output with prompt synthesis and applies suggested patches
- Provider selection via `AiProviderCondition` (cloud-only, ollama-only, both, or disabled)
- Rate-limited per `RateLimitProperties`

### Learn Center
- Interactive in-app CQL tutorials: introduction, language reference, concept guide, advanced topics, troubleshooting
- CQL Playground with live translation/execution against synthetic data
- CQL quiz with progress tracking
- eCQM-specific tutorial walkthrough
- TW Core IG guide with profile/extension explanations
- FHIRPath ⇄ ELM crosswalk reference

### Okta SSO Integration
- OIDC authentication via Okta with PKCE flow
- `OktaOidcService` exchanges authorization code for tokens; `OktaUserInfo` maps to internal `UserEntity`
- JIT user provisioning with role mapping from Okta groups
- Coexists with local username/password auth (configurable per deployment)

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
- Data requirements analysis for measures
- Measure versioning with history, sharing, ownership transfer, and access control
- Workflow lifecycle: draft → submit for review → approve/reject → retire
- Measure locking to prevent concurrent edits
- Measure validation (full and quick) with care setting classification and QI-Core profile suggestions
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
- Patient demographics search, resource validation (with optional profile URL), batch/transaction operations
- Bulk Data Export ($export) with async polling
- VSAC (Value Set Authority Center) integration for terminology
- Terminology operations: ValueSet expand, CodeSystem validate-code, lookup, search
- Implementation Guide support: packages, profiles, value sets, code systems
- FHIR structure definition introspection for resource metadata
- ValueSet caching with per-cache TTL and admin eviction
- Circuit breakers and retry with exponential backoff on all FHIR calls

### Internationalization (i18n)
- Multi-language support: English (en) and Traditional Chinese (zh-TW)
- Language switcher in header toolbar with persistent preference (localStorage, with Safari Private mode safe fallback via `safeStorage.ts`)
- MUI locale integration for component-level translations
- 14 namespaces: `common`, `validation`, `editor`, `builder`, `authoring`, `ecqm`, `cds`, `fhir`, `terminology`, `measures`, `admin`, `cqlLibraries`, `landing`, `patientGenerator`
- Help tooltips with automatic i18n key resolution

### Administration
- User management: create users, assign roles, enable/disable accounts
- Personal API key management (create, list, revoke)
- Password reset: self-service email flow and admin-initiated reset
- Audit dashboard with log search, filtering, and CSV export
- Audit statistics: daily activity, top users, action breakdowns
- Compliance views: PHI access tracking, login activity, security events
- SMART on FHIR configuration endpoint

### Frontend UX
- Dark mode with persistent user preferences (editor font size, tab size, word wrap, minimap)
- Design system: GradientButton, StatusChip, SectionHeader, WorkflowIndicator
- Lazy loading with skeleton screens (CardListSkeleton, DashboardSkeleton, TableSkeleton)
- React Error Boundaries preventing white-screen crashes with isolated per-page recovery
- Unsaved changes guard on measure editor tabs
- Global toast notification system
- Recent/favorites sidebar for quick library access with server-side persistence
- Client-side input validation with DTO validation and JSON body XSS filtering
- Inline help tooltips and quick-start guide drawer
- Configurable backend URLs via environment variables

### Security & Operations
- JWT authentication with RBAC (admin/user roles)
- JWT Token Version revocation: logout / password change / role change / account disable invalidates issued tokens within 30 seconds (Caffeine cache TTL)
- Refresh token rotation with reuse detection
- Account lockout after configurable failed-login threshold (V53 migration)
- Personal API keys with bcrypt hash storage and revocation
- AES-256-GCM encryption at rest, TLS in transit, hardened HTTP headers (HSTS, CSP)
- Audit logging of all API access with full context, request ID correlation, and configurable retention
- CSV injection prevention in all export endpoints (audit logs, measure reports)
- Rate limiting (`RateLimitProperties`), XSS sanitization (DOMPurify), FHIR resource type whitelisting
- Prometheus + Grafana monitoring with custom CQL/CDS/measure metrics; Alertmanager rules
- Resilience4j circuit breakers, connection pooling, execution thread pool with queuing
- Sentry-compatible error tracking via `ErrorTrackingConfig`
- Kubernetes manifests, network policies, sealed secrets, and GitHub Actions CI/CD
- TFDA / IEC 62304 / ISO 14971 regulatory document generation pipeline (see `regulatory_docs/`)
- Local integration smoke test harness (`scripts/smoke/run.sh`) covering all four scoring families end-to-end

## Project Structure

```
├── backend/                    # Java Spring Boot backend
│   ├── pom.xml
│   └── src/main/java/com/cqlplatform/
│       ├── config/             # Spring config: SecurityConfig, AsyncConfig, CqlConfig, MetricsConfig,
│       │                       # AiProperties, OktaProperties, OllamaProperties, RateLimitProperties,
│       │                       # ErrorTrackingConfig, DataInitializer, EmailHashMigration
│       ├── controller/         # 19 REST controllers
│       │   ├── AuthController              # Login, register, password reset, refresh tokens
│       │   ├── CqlController               # CQL translate, validate, execute, libraries
│       │   ├── CdsHooksController          # CDS service discovery and invocation
│       │   ├── CdsServiceConfigController  # CDS service management
│       │   ├── AuthoringController         # CDS artifact CRUD, CQL generation, testing, deploy
│       │   ├── EcqmController              # eCQM artifact CRUD, CQL generation, publish
│       │   ├── MeasureController           # Measures, test cases, reports, schedules, dashboard
│       │   ├── FhirController              # FHIR resources, terminology, structure defs
│       │   ├── EhrIntegrationController    # SMART backend services, patient import, batch jobs
│       │   ├── DepartmentController        # Department CRUD for measure/dashboard scoping
│       │   ├── IndicatorCatalogController  # Indicator catalog browsing
│       │   ├── NotificationController      # In-app notifications
│       │   ├── SandboxPresetController     # Sandbox presets for CDS/eCQM testing
│       │   ├── SettingsController          # System settings
│       │   ├── VersionController           # Build/version metadata
│       │   ├── AdminController             # User management
│       │   ├── AuditController             # Audit dashboard
│       │   ├── SmartConfigController       # SMART on FHIR configuration
│       │   ├── UserApiKeyController        # Personal API key management
│       │   └── UserLibraryPrefsController  # Library favorites and recent history
│       ├── entity/             # 37 JPA entities (incl. EhrConnection, BatchImportJob, FhirSubscription,
│       │                       # MeasureThreshold, IndicatorCatalog, Notification, RefreshToken)
│       ├── repository/         # 36 Spring Data repositories
│       ├── security/           # JWT auth, API key auth, TokenVersionService (immediate revocation)
│       ├── service/
│       │   ├── cql/            # CQL translation, execution, library management, dependency analysis
│       │   ├── fhir/           # FHIR data provider, validation, bulk export, VSAC, EHR connection,
│       │   │                   # SMART backend tokens, TLS context, patient import/match, subscriptions
│       │   ├── cds/            # CDS Hooks: CRUD, invocation, card strategies, analytics, feedback, sandbox
│       │   ├── measure/        # 27 services — eCQM evaluation, reports, scheduling, comparison,
│       │   │                   # composite measures, batch evaluation, dashboard, indicator catalog,
│       │   │                   # date shift, HQMF/QRDA export, human-readable rendering
│       │   ├── authoring/      # CDS authoring: artifact CRUD, CQL generation, testing, import, validation
│       │   ├── ecqm/           # eCQM authoring: artifact CRUD, CQL builder, publish, validation
│       │   └── ai/             # CloudAiService, OllamaService, CqlFixService, CqlKnowledgeBase
│       ├── model/              # DTOs and request/response models
│       │   ├── auth/           # Auth and admin DTOs
│       │   ├── audit/          # Audit log DTOs
│       │   ├── authoring/      # Authoring DTOs (templates, modifiers, artifacts)
│       │   ├── fhir/           # FHIR structure definition DTOs
│       │   ├── ecqm/           # eCQM DTOs (request, response, summary, constants, publish result)
│       │   └── measure/        # Measure-specific DTOs (incl. dashboard, threshold, indicator catalog)
│       ├── util/               # Shared utilities (CsvUtils)
│       ├── validation/         # Input validators (XSS, FHIR type whitelist)
│       └── exception/          # Global exception handling
│
├── frontend/                   # React + TypeScript frontend
│   ├── package.json
│   ├── .env.example            # Environment variable template
│   └── src/
│       ├── i18n.ts             # i18next configuration (en + zh-TW, 14 namespaces)
│       ├── components/
│       │   ├── common/         # GradientButton, StatusChip, SectionHeader, ErrorBoundary,
│       │   │                   # LanguageSwitcher, HelpTooltip, HelpDrawer, Skeletons, TabPanel
│       │   ├── landing/        # Marketing landing page sections
│       │   ├── editor/         # CqlEditor, ElmViewer, LibraryQuickAccess, VersionHistory, DiffViewer
│       │   ├── execution/      # ExecutionPanel, DebugPanel
│       │   ├── debug/          # Diagnostic panels for execution traces
│       │   ├── builder/        # CqlBuilderPanel, IncludesSection, ValueSetSection, CodesSection,
│       │   │                   # ConceptsSection, ParametersSection, DefinitionsSection, FunctionsSection,
│       │   │                   # RetrieveBuilder, QueryBuilder, CdsCardBuilder, OperatorPanel,
│       │   │                   # ExpressionBuilder, CqlPreviewBox, SnippetPreview
│       │   ├── authoring/      # CDS Authoring Tool (11-tab workspace + builder/fields/element-select)
│       │   ├── ecqm/           # eCQM Authoring Tool (8-tab workspace)
│       │   ├── cds/            # CdsPanel (invoke, manage, analytics, sandbox)
│       │   ├── measure/        # MeasureEditor, MeasureLibrary, WorkflowIndicator, PopulationCriteriaTab
│       │   ├── dashboard/      # ScoreTrendChart, ThresholdAlertPanel, QualityReportPanel,
│       │   │                   # ScoreDistributionChart, DepartmentDrilldownChart, DashboardFilterBar
│       │   ├── cql-libraries/  # Library workspace (editor, metadata, dependency, history, sharing tabs)
│       │   ├── ehr/            # EHR connection forms, patient import dialog/history, search
│       │   ├── patient-generator/ # TW Core IG synthetic patient generator (7 components)
│       │   ├── learn/          # In-app Learn Center (tutorials, playground, quiz, cheat sheet)
│       │   ├── testcase-builder/  # VisualBundleBuilder, ResourceForm, ElementField
│       │   ├── fhir/           # FhirBrowser
│       │   ├── terminology/    # TerminologyBrowser
│       │   ├── layout/         # Header, Footer
│       │   └── auth/           # ProtectedRoute, login/forgot-password forms
│       ├── contexts/           # NotificationContext, PreferencesContext, LibraryHistoryContext,
│       │                       # BundleBuilderContext, ResourceTypeContext, EhrOutageContext,
│       │                       # TerminologyDrawerContext
│       ├── hooks/              # 36 custom hooks (useCql, useMeasures, useAuthoring, useEcqm,
│       │                       # usePatientGenerator, useEhrConnection, useDashboard, useLibraryPrefs, …)
│       ├── pages/              # 17 pages — EditorPage, CdsPage, AuthoringPage, EcqmPage, MeasuresPage,
│       │                       # MeasureDashboardPage, FhirPage, TerminologyPage, CqlLibrariesPage,
│       │                       # PatientGeneratorPage, LearnPage, LandingPage, AdminUsersPage,
│       │                       # AuditDashboardPage, LoginPage, ForgotPasswordPage, ResetPasswordPage,
│       │                       # OktaCallbackPage
│       ├── locales/            # i18n translation files (14 namespaces × 2 languages)
│       │   ├── en/             # common, validation, editor, builder, authoring, ecqm, cds, fhir,
│       │   │                   # terminology, measures, admin, cqlLibraries, landing, patientGenerator
│       │   └── zh-TW/          # (same 14 namespaces, Traditional Chinese)
│       ├── api/                # 19 Axios API client modules
│       ├── config/             # twcore/ — TW Core synthetic patient generator JSON config
│       ├── store/              # Redux slices (auth, editor, execution, artifact)
│       ├── utils/              # 24 modules — cqlSyntax, dashboardFormat, fhirPatientGenerator,
│       │                       # twDemographics, scoringFamily, safeStorage, random, …
│       ├── constants/          # 17 modules — timing, layout, queryConstants, eCQM scoring/population
│       ├── theme.ts            # MUI theme with light/dark mode and locale support
│       └── types/              # TypeScript interfaces
│
├── docker/                     # Docker configuration
│   ├── docker-compose.yml      # Production (only port 80/443 exposed)
│   ├── docker-compose.dev.yml  # Dev overlay (all ports exposed)
│   ├── docker-compose.dev-pg.yml # Local Postgres-only for backend dev
│   ├── .env.example            # Secrets template
│   ├── nginx.conf              # Reverse proxy with TLS, Grafana, Prometheus
│   ├── alertmanager.yml        # Alert routing
│   ├── prometheus.yml          # Metrics scraping
│   ├── prometheus-alerts.yml   # Alert rules
│   └── scripts/                # backup-db.sh, restore-db.sh
│
├── e2e/                        # Playwright end-to-end tests
├── twcore/                     # TWCore synthetic patient data generator (Python)
├── TWCOREDATA/                 # Taiwan Core FHIR patient data and IG material
├── k8s/                        # Kubernetes manifests (incl. sealed secrets, network policies, staging)
├── load-tests/                 # k6 load testing scripts
├── scripts/
│   ├── smoke/                  # Integration smoke test harness (one canonical scenario per scoring family)
│   ├── changelog/              # CHANGE_LOG hash backfill helpers
│   ├── apply-staging.sh        # Staging deploy helper
│   └── seal-secrets.sh         # Sealed-secrets utility
├── regulatory_docs/            # TFDA / IEC 62304 / ISO 14971 document generation
│   ├── scripts/                # generate_regulatory_docs.py, generate_test_report.py
│   ├── templates/              # 6 Jinja2 templates (SRS, SDS, risk, verification, traceability, change control)
│   └── output/                 # Generated documents
├── docs/                       # Admin guide, deployment guide, runbooks, ADRs, compliance
└── .github/workflows/          # CI/CD: ci, deploy, release, regulatory-check, regulatory-docs,
                                #         regulatory-snapshot, changelog-backfill
```

## Prerequisites

- Java 21+
- Node.js 18+
- Maven 3.9+
- PostgreSQL 16 (or use the bundled `docker-compose.dev-pg.yml`)
- Docker & Docker Compose (recommended for dev)

## Quick Start

### Backend

```bash
# Start a local Postgres (first time only)
docker compose -f docker/docker-compose.dev-pg.yml up -d

# Run the backend (uses dev profile against local Postgres)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
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
| `/api/cql/libraries/{id}/dependency-analysis` | GET | Library dependency analysis with version conflict detection |

### CDS Hooks

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/cds-services` | GET | CDS service discovery |
| `/cds-services/{id}` | POST | Invoke CDS service |
| `/cds-services/{id}/feedback` | POST | Submit CDS feedback |
| `/cds-services/{id}/sandbox` | POST | Sandbox invocation |
| `/api/cds/services` | GET/POST/PUT/DELETE | Manage CDS service configs |
| `/api/cds/services/analytics` | GET | Service usage analytics |

### CDS Authoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/authoring/artifacts` | GET/POST | List / create CDS artifacts |
| `/api/authoring/artifacts/{id}` | GET/PUT/DELETE | Single artifact CRUD |
| `/api/authoring/artifacts/{id}/duplicate` | POST | Duplicate artifact |
| `/api/authoring/templates` | GET | List form templates |
| `/api/authoring/modifiers` | GET | List available modifiers |
| `/api/authoring/artifacts/{id}/cql` | POST | Generate CQL from artifact tree |
| `/api/authoring/artifacts/{id}/elm` | POST | Generate ELM from artifact |
| `/api/authoring/artifacts/{id}/validate` | POST | Validate artifact CQL |
| `/api/authoring/artifacts/{id}/external-cql` | GET | List external CQL libraries |
| `/api/authoring/artifacts/{id}/external-cql/upload` | POST | Upload external CQL file |
| `/api/authoring/artifacts/{id}/external-cql/content` | POST | Add external CQL from content |
| `/api/authoring/artifacts/{artifactId}/external-cql/{libId}` | GET/DELETE | Get/delete external lib |
| `/api/authoring/artifacts/{id}/test` | POST | Test artifact against patient |
| `/api/authoring/artifacts/{id}/deploy-cds` | POST | Deploy artifact as CDS service |
| `/api/authoring/artifacts/{id}/save-library` | POST | Save artifact as CQL library |
| `/api/authoring/artifacts/{id}/summary` | GET | Get artifact summary |
| `/api/authoring/import-cql` | POST | Import CQL into artifact structure |
| `/api/authoring/query-builder/resources` | GET | FHIR resources for query builder |
| `/api/authoring/query-builder/operators` | GET | Operators for query builder |
| `/api/authoring/twcore-catalog` | GET | TWCore catalog entries |
| `/api/authoring/twcore-catalog/code-systems` | GET | TWCore code systems |

### eCQM Authoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ecqm/artifacts` | GET/POST | List / create eCQM artifacts |
| `/api/ecqm/artifacts/{id}` | GET/PUT/DELETE | Single eCQM artifact CRUD |
| `/api/ecqm/artifacts/{id}/duplicate` | POST | Duplicate eCQM artifact |
| `/api/ecqm/artifacts/{id}/cql` | POST | Generate CQL from eCQM artifact |
| `/api/ecqm/artifacts/{id}/elm` | POST | Generate CQL and translate to ELM |
| `/api/ecqm/artifacts/{id}/validate` | POST | Validate eCQM CQL |
| `/api/ecqm/artifacts/{id}/publish` | POST | Publish to MeasureDefinition |
| `/api/ecqm/templates` | GET | Element templates (delegated) |
| `/api/ecqm/modifiers` | GET | Available modifiers (delegated) |
| `/api/ecqm/scoring-types` | GET | Scoring type configurations with required populations |

### Quality Measures

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/measures` | GET/POST/PUT/DELETE | Manage measure definitions |
| `/api/measures/{id}/$evaluate-measure` | POST | Evaluate quality measure |
| `/api/measures/{id}/cql-expressions` | GET | List CQL expressions for a measure |
| `/api/measures/{id}/data-requirements` | GET | Data requirements analysis |
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
| `/api/fhir/$validate` | POST | Validate FHIR resource (optional `?profile=` URL) |
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

### User Preferences

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/cql/user-prefs/favorites` | GET | List favorite libraries |
| `/api/cql/user-prefs/favorites/{libraryId}` | POST/DELETE | Add/remove favorite |
| `/api/cql/user-prefs/recent` | GET | List recent libraries |
| `/api/cql/user-prefs/recent/{libraryId}` | POST | Add to recent |
| `/api/cql/user-prefs/recent` | DELETE | Clear recent history |
| `/api/user/api-keys` | GET/POST | List / create API keys |
| `/api/user/api-keys/{id}` | DELETE | Revoke API key |

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

### EHR Integration

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/ehr/connections` | GET/POST | List / create EHR FHIR endpoints |
| `/api/ehr/connections/{id}` | GET/PUT/DELETE | Single connection CRUD |
| `/api/ehr/connections/{id}/health` | GET | Connection health check |
| `/api/ehr/connections/{id}/test` | POST | Test SMART backend authentication |
| `/api/ehr/connections/{id}/import` | POST | Trigger async patient import |
| `/api/ehr/imports` | GET | Patient import history |
| `/api/ehr/imports/{id}` | GET | Single import status |
| `/api/ehr/imports/{id}/retry` | POST | Retry failed import |
| `/api/ehr/batch-jobs` | GET/POST | Batch import jobs |
| `/api/ehr/subscriptions` | GET/POST | FHIR Subscription registration |

### Departments & Indicator Catalog

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/departments` | GET/POST/PUT/DELETE | Department CRUD for measure scoping |
| `/api/indicator-catalog` | GET | Browse indicator catalog |
| `/api/indicator-catalog/{id}` | GET | Single indicator detail |

### Notifications & Sandbox

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/notifications` | GET | List user notifications |
| `/api/notifications/{id}/read` | POST | Mark notification as read |
| `/api/sandbox-presets` | GET/POST | Sandbox presets for CDS/eCQM testing |

### Settings & Version

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/settings` | GET | System settings (read) |
| `/api/settings` | PUT | Update settings (admin) |
| `/api/version` | GET | Build metadata (commit, version, build time) |

### Other

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/.well-known/smart-configuration` | GET | SMART on FHIR configuration |
| `/actuator/health` | GET | Spring Boot health probe |
| `/actuator/prometheus` | GET | Prometheus metrics endpoint |

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
| `JWT_REFRESH_SECRET` | Refresh token signing secret | (required in production) |
| `ENCRYPTION_KEY` | AES-256 encryption key | (required in production) |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/cqlplatform` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (dev/docker/prod) | `dev` |
| `VSAC_API_KEY` | VSAC API key for terminology | (optional) |
| `AUDIT_RETENTION_DAYS` | Audit log retention period | `365` |
| `OKTA_ISSUER_URI` | Okta OIDC issuer URI | (optional) |
| `OKTA_CLIENT_ID` | Okta OIDC client ID | (optional) |
| `OKTA_CLIENT_SECRET` | Okta OIDC client secret | (optional) |
| `AI_PROVIDER` | `cloud`, `ollama`, `both`, or `disabled` | `disabled` |
| `AI_CLOUD_API_KEY` | API key for cloud AI provider | (required if AI cloud enabled) |
| `OLLAMA_BASE_URL` | Local Ollama server URL | `http://localhost:11434` |
| `RATE_LIMIT_REQUESTS_PER_MINUTE` | Per-user rate limit | `120` |
| `EMAIL_SMTP_HOST` | SMTP host for password reset emails | (optional) |
| `SENTRY_DSN` | Sentry DSN for error tracking | (optional) |

## Key Dependencies

### Backend
- Spring Boot 3.5.12 (Java 21, Tomcat 10.1.x)
- CQL Framework (cql-to-elm, engine) 4.5.0
- HAPI FHIR 8.6.6
- CQF Clinical Reasoning
- Resilience4j 2.2.0
- PostgreSQL 16 (prod & dev) / H2 (test only)
- Flyway 55+ forward migrations under `db/migration/` (manual rollback scripts under `db/rollback/`)
- Caffeine in-process cache (token version, ValueSet)
- Apache POI (Excel export)
- FreeMarker (CQL template engine)
- Spring Security 6.5.x with JWT

### Frontend
- React 18 + TypeScript 5.3
- Vite 5
- Monaco Editor (@monaco-editor/react 4.6)
- Material-UI 7.3
- Redux Toolkit 2.0
- TanStack Query (React Query) 5.x
- i18next 25 + react-i18next 16 + i18next-browser-languagedetector
- Axios 1.15
- React Router 6.20
- Recharts 3.7 (dashboard charts)
- DOMPurify 3.3 (XSS sanitization)
- react-window (virtualized lists)
- react-helmet-async (head management)
- Vitest + React Testing Library (unit tests)
- Playwright (e2e)

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

### Integration Smoke Test

Before pushing changes that touch the CQL pipeline, run the local Docker smoke harness — it covers all four scoring families (proportion / ratio / continuous-variable / cohort) end-to-end (save → publish → evaluate):

```bash
scripts/smoke/run.sh           # Run all scenarios (~60–120s)
scripts/smoke/run.sh --keep    # Keep stack running for debugging
```

See `scripts/smoke/README.md` for details.

### TFDA / IEC 62304 Regulatory Documents

Issues / PRs are written in Traditional Chinese and labelled per IEC 62304 / ISO 14971. Generated documents go to `regulatory_docs/output/`:

```bash
GITHUB_TOKEN=$(gh auth token) python regulatory_docs/scripts/generate_regulatory_docs.py \
    --repo Lusnaker0730/CQL --version 1.0.0

python regulatory_docs/scripts/generate_test_report.py \
    --backend-reports backend/target/surefire-reports \
    --output regulatory_docs/output --version 1.0.0
```

See `CLAUDE.md` ("TFDA 法規文件工作流") for the issue templates and CI gating rules.

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
| Ctrl+Z | Undo (builder operations) |
| Ctrl+Y | Redo (builder operations) |

## License

MIT License

## References

- [CQL Specification](https://cql.hl7.org/)
- [CDS Hooks](https://cds-hooks.org/)
- [FHIR](https://www.hl7.org/fhir/)
- [HAPI FHIR](https://hapifhir.io/)
- [CMS 2026 eCQM Logic and Implementation Guidance v9.0](https://ecqi.healthit.gov/)
