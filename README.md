# CQL Platform

A comprehensive Clinical Quality Language (CQL) development platform featuring CQL editing, translation, execution, CDS Hooks integration, and quality measure evaluation.

## Features

- **CQL Editor**: Monaco-based editor with syntax highlighting, auto-completion, and error diagnostics
- **CQL Translation**: Real-time translation of CQL to ELM (Expression Logical Model)
- **CQL Execution**: Execute CQL expressions against FHIR servers
- **CDS Hooks**: Clinical Decision Support Hooks integration
- **Quality Measures**: eCQM (electronic Clinical Quality Measure) evaluation
- **FHIR Browser**: Browse and explore FHIR resources

## Project Structure

```
D:\CQL\
├── backend/                    # Java Spring Boot backend
│   ├── pom.xml
│   └── src/main/java/com/cqlplatform/
│       ├── CqlPlatformApplication.java
│       ├── config/             # Spring configuration
│       ├── controller/         # REST API controllers
│       ├── service/            # Business logic
│       │   ├── cql/            # CQL translation & execution
│       │   ├── fhir/           # FHIR integration
│       │   ├── cds/            # CDS Hooks
│       │   └── measure/        # Quality measures
│       ├── model/              # Data models
│       └── exception/          # Exception handling
│
├── frontend/                   # React frontend
│   ├── package.json
│   └── src/
│       ├── components/         # React components
│       ├── pages/              # Page components
│       ├── api/                # API clients
│       ├── hooks/              # Custom React hooks
│       ├── store/              # Redux store
│       └── types/              # TypeScript types
│
└── docker/                     # Docker configuration
    ├── docker-compose.yml
    ├── Dockerfile.backend
    ├── Dockerfile.frontend
    └── nginx.conf
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

The backend will start at `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend will start at `http://localhost:5173`.

### Docker

```bash
cd docker
docker-compose up -d
```

This will start:
- Backend at `http://localhost:8080`
- Frontend at `http://localhost:5173`
- HAPI FHIR Server at `http://localhost:8090/fhir`

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/cql/translate` | POST | Translate CQL to ELM |
| `/api/cql/validate` | POST | Validate CQL syntax |
| `/api/cql/execute` | POST | Execute CQL |
| `/api/cql/libraries` | GET/POST | Manage CQL libraries |
| `/cds-services` | GET | CDS service discovery |
| `/cds-services/{id}` | POST | Invoke CDS service |
| `/api/measures/{id}/$evaluate-measure` | POST | Evaluate quality measure |
| `/api/fhir/{resourceType}` | GET/POST | FHIR resource operations |

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
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `FHIR_SERVER_URL` | FHIR server URL | `http://hapi.fhir.org/baseR4` |
| `FHIR_TERMINOLOGY_URL` | Terminology server URL | `http://tx.fhir.org/r4` |

## Key Dependencies

### Backend
- Spring Boot 3.2
- CQL Framework (cql-to-elm, engine) 3.29.0
- HAPI FHIR 7.0.0
- CQF Clinical Reasoning 3.0.0

### Frontend
- React 18
- Monaco Editor
- Material-UI 5
- Redux Toolkit
- TanStack Query

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
# Backend
cd backend
mvn test

# Frontend
cd frontend
npm test
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

## License

MIT License

## References

- [CQL Specification](https://cql.hl7.org/)
- [CDS Hooks](https://cds-hooks.org/)
- [FHIR](https://www.hl7.org/fhir/)
- [HAPI FHIR](https://hapifhir.io/)
