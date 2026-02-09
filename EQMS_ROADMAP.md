# eQMS Improvement Roadmap

> Based on MADiE architecture patterns, adapted for CQL Platform

---

## Phase 1: Measure Editor Tab-Based Restructure + Backend Decomposition [COMPLETED]

### Goal
Refactor MeasuresPage from flat tab layout to a **measure-centric editing experience** with sub-tabs per measure (like MADiE's EditMeasure pattern). Decompose `MeasureEvaluationService` (451 lines) into focused single-responsibility services.

### Completed Frontend

- [x] **1.1** `MeasureEditor.tsx` — 5-tab container (Details | CQL | Population Criteria | Evaluate | Reports)
- [x] **1.2** `MeasureDetailsTab.tsx` — Inline metadata editing with save/cancel
- [x] **1.3** `MeasureCqlTab.tsx` — Embedded CQL editor with translate + save
- [x] **1.4** `PopulationCriteriaTab.tsx` — Visual population editor with CQL expression dropdowns
- [x] **1.5** `MeasureEvaluationTab.tsx` — Refactored from MeasurePanel (478→150 lines)
- [x] **1.6** `MeasuresPage.tsx` — Two-panel layout (Library 4col | Editor 8col)
- [x] **1.7** `EvaluationResultCard.tsx` — Reusable result display component
- [x] **1.8** `useUnsavedChangesGuard.ts` — Browser beforeunload guard

### Completed Backend

- [x] **1.9** `PatientDiscoveryService.java` — Patient query logic
- [x] **1.10** `PopulationEvaluator.java` — Population counting (pure logic)
- [x] **1.11** `StratifierEvaluator.java` — Stratification logic (pure logic)
- [x] **1.12** `MeasureScoreCalculator.java` — Scoring (proportion/ratio/cohort)
- [x] **1.13** `MeasureEvaluationContext.java` — Immutable value object
- [x] **1.14** `MeasureEvaluationService.java` — Refactored to orchestrator (451→271 lines)
- [x] **1.15** `GET /api/measures/{id}/cql-expressions` — Expression names endpoint

### Architecture (Completed)

```
MeasuresPage
├── Tab: Measures
│   ├── MeasureLibrary (left 4col, persistent)
│   └── MeasureEditor (right 8col, 5 sub-tabs)
│       ├── MeasureDetailsTab
│       ├── MeasureCqlTab (Monaco + translate + save)
│       ├── PopulationCriteriaTab (visual editor + expression dropdowns)
│       ├── MeasureEvaluationTab + EvaluationResultCard
│       └── MeasureReportHistory
└── Tab: Comparison & Trends
    └── MeasureComparison

Backend: MeasureEvaluationService (orchestrator)
├── PatientDiscoveryService
├── CqlExecutionService (per patient)
├── PopulationEvaluator
├── StratifierEvaluator
├── MeasureScoreCalculator
└── MeasureReportService
```

---

## Phase 2: CQL Builder Panel (Visual Constructor) [COMPLETED]

### Goal
Add a visual CQL builder panel alongside Monaco Editor on the **EditorPage**, allowing non-programmers to construct CQL visually (inspired by MADiE's CqlBuilderPanel with Allotment split pane).

### Design Decisions
- **Location**: EditorPage — the builder panel replaces the right-side ELM/Execute tabs when toggled
- **Backend**: Reuse existing `POST /api/cql/translate` which already returns full metadata (valueSets, codes, includes, parameters, expressions) — no new endpoint needed
- **VSAC/Terminology**: Reuse existing `fhirApi.vsacSearchValueSets()`, `fhirApi.vsacExpandValueSet()`, `useTerminology` hooks
- **Library management**: Reuse existing `GET /api/cql/libraries` and `GET /api/cql/libraries/metadata`
- **Sync strategy**: One-way builder→editor (builder generates CQL snippets inserted at cursor); editor→builder via translate metadata on demand

### Completed Frontend

- [x] **2.1** `CqlBuilderPanel.tsx` — Accordion-based builder container
  - 6 sections: Includes, ValueSets, Codes, Parameters, Definitions, Functions
  - Each section collapsible with count badge
  - "Parse CQL" button re-parses current editor content to populate sections
  - Read-only display of existing CQL structures + "Add New" forms

- [x] **2.2** `IncludesSection.tsx` — Library dependency manager
  - Lists current includes parsed from CQL metadata
  - "Add Include" form: Autocomplete from `GET /api/cql/libraries/metadata`
  - Version selection from `GET /api/cql/libraries/versions/{name}`
  - Generates `include "{name}" version '{version}' called {alias}` and inserts into editor

- [x] **2.3** `ValueSetSection.tsx` — ValueSet browser + inserter
  - Lists current valueSets parsed from CQL metadata
  - "Add ValueSet" form with VSAC search (reuses `useSearchValueSets` hook)
  - Preview expansion with collapsible code table (reuses `useExpandValueSet` hook)
  - Generates `valueset "Name": 'url'` and inserts into editor

- [x] **2.4** `CodesSection.tsx` — Code system browser
  - Lists current codes parsed from CQL metadata
  - "Add Code" form: 7 common code systems (LOINC, SNOMED, RxNorm, ICD-10, CPT, etc.)
  - Code lookup validation (reuses `useLookupCode` hook) with auto-fill display name
  - Generates `codesystem` + `code` CQL declarations

- [x] **2.5** `ParametersSection.tsx` — Parameter manager
  - Lists current parameters parsed from CQL metadata
  - "Add Parameter" form: name, 17 CQL type options, optional default value
  - Generates `parameter "Name" Type default value` declaration

- [x] **2.6** `DefinitionsSection.tsx` — Expression builder
  - Lists current definitions parsed from CQL metadata with return type chips
  - "Add Definition" form: name, context dropdown (Patient/Population), expression template
  - 6 templates: Blank, Age Filter, Condition Check, Encounter Check, Medication Check, Observation Value
  - Generates `define "Name": expression` block

- [x] **2.7** `FunctionsSection.tsx` — Function builder
  - Lists current functions parsed from CQL metadata
  - "Add Function" form: name, dynamic argument list (name + type), return expression
  - 16 argument types including FHIR resource types
  - Generates `define function "Name"(arg Type): expression` block

- [x] **2.8** Integrated builder into `EditorPage.tsx`
  - Toggle button with Construction icon in editor header bar
  - When on: right panel shows CqlBuilderPanel (border color changes to primary teal)
  - When off: original ELM/Execute tabs (border color is secondary navy)
  - Snippet insertion at cursor position via Redux dispatch

- [x] **2.9** `useCqlStructure` hook — Editor↔Builder sync
  - Calls translate API with current cqlContent
  - Extracts metadata (includes, valueSets, codes, parameters, expressions, functions)
  - Debounced (2s after last edit) for live update + manual "Parse CQL" trigger
  - Returns structured CQL data for builder sections

### Architecture (Completed)

```
EditorPage
├── LibraryQuickAccess (left 2col)
├── CqlEditor Paper (center 5.5col)
│   └── Header: Translate | Save | Export | Import | [Builder Toggle]
└── Right Panel Paper (right 4.5col)
    ├── Mode A (default): ELM/Errors | Execute tabs
    └── Mode B (builder): CqlBuilderPanel
        ├── Header: "CQL Builder" + Parse CQL button
        ├── Includes accordion (IncludesSection)
        ├── Value Sets accordion (ValueSetSection)
        ├── Codes accordion (CodesSection)
        ├── Parameters accordion (ParametersSection)
        ├── Definitions accordion (DefinitionsSection)
        └── Functions accordion (FunctionsSection)

Hook: useCqlStructure → POST /api/cql/translate → TranslationMetadata
```

---

## Phase 3: Test Cases Management [COMPLETED]

### Goal
Add patient test case management for measures (like MADiE's testCases feature).

### Completed Backend

- [x] **3.4** `TestCase.java` + `TestCaseRunResult.java` — Model DTOs
  - TestCase: id, measureDefinitionId, title, description, patientBundleJson, expectedPopulations, status, lastRunActualPopulations
  - TestCaseRunResult: testCaseId, status (pass/fail/error), expectedPopulations, actualPopulations, comparisons list, executionTimeMs
  - PopulationComparison: populationType, expected, actual, match boolean
- [x] **3.4** `TestCaseEntity.java` — JPA entity with JSON serialization for population maps
  - @PrePersist/@PreUpdate serialization, @PostLoad deserialization (matching existing patterns)
- [x] **3.4** `TestCaseRepository.java` — JPA repository
  - findByMeasureDefinitionIdOrderByCreatedAtAsc, countByMeasureDefinitionId, deleteByMeasureDefinitionId
- [x] **3.5** `TestCaseService.java` — CRUD + execution
  - CRUD: getTestCasesForMeasure, getById, create, update, delete
  - Execution: runTestCase (single), runAllTestCases (batch)
  - Compares expected vs actual population membership with per-population pass/fail detail
  - Extracts patient ID from FHIR Bundle JSON for CQL execution context
  - Maps population criteria expressions from GroupDefinitions to population types
- [x] **3.6** REST endpoints added to `MeasureController.java`
  - `GET /api/measures/{id}/test-cases` — List test cases
  - `GET /api/measures/{id}/test-cases/{tcId}` — Get test case
  - `POST /api/measures/{id}/test-cases` — Create test case
  - `PUT /api/measures/{id}/test-cases/{tcId}` — Update test case
  - `DELETE /api/measures/{id}/test-cases/{tcId}` — Delete test case
  - `POST /api/measures/{id}/test-cases/{tcId}/run` — Run single test case
  - `POST /api/measures/{id}/test-cases/run` — Run all test cases

### Completed Frontend

- [x] **3.1** `TestCasesTab.tsx` — Test case list & management
  - Lists all test cases with status icons (pass/fail/error/pending)
  - Pass/fail summary chips in header
  - "Run All" button for batch execution
  - "Add Test Case" button
  - Per-test-case: Run, Edit, Delete actions
  - Inline result display after execution with TestCaseResult component
- [x] **3.2** `TestCaseEditor.tsx` — FHIR Patient Bundle editor
  - Title, description fields
  - Expected population membership toggles (6 population types with Switch controls)
  - FHIR Bundle JSON editor (monospace textarea) with JSON validation
  - Default patient bundle template
  - Create / Update modes with save mutation
- [x] **3.3** `TestCaseResult.tsx` — Expected vs actual comparison
  - Status chip (pass/fail/error) with colored icon
  - Population comparison table: Population | Expected | Actual | Result
  - Per-row pass/fail icons
  - Execution time display
  - Error message display for failed executions
- [x] **3.1** Integrated into `MeasureEditor.tsx` as 6th tab (between Evaluate and Reports)
- [x] Types added: `TestCase`, `TestCaseRunResult`, `PopulationComparison`
- [x] API methods added: getTestCases, createTestCase, updateTestCase, deleteTestCase, runTestCase, runAllTestCases

### Architecture (Completed)

```
MeasureEditor
├── Tab: Details
├── Tab: CQL
├── Tab: Population Criteria
├── Tab: Evaluate
├── Tab: Test Cases (NEW)
│   └── TestCasesTab
│       ├── Test case list with status icons
│       ├── Run All / Add buttons
│       ├── Per-test: Run / Edit / Delete
│       ├── TestCaseEditor (create/edit modal)
│       └── TestCaseResult (inline comparison table)
└── Tab: Reports

Backend:
TestCaseService
├── CRUD → TestCaseRepository → TestCaseEntity
├── runTestCase → CqlExecutionService.execute()
│   ├── Extract patient ID from Bundle
│   ├── Execute CQL with patient context
│   ├── Map expression results → population membership
│   └── Compare expected vs actual → pass/fail
└── runAllTestCases → batch execution + persist results
```

---

## Phase 4: Design System + UX Polish [COMPLETED]

### Goal
Extract reusable components from repeated patterns, add unsaved-changes guards globally, add workflow status indicators, and add contextual help tooltips throughout the measure editor.

### Completed Components

- [x] **4.1** `GradientButton.tsx` — Reusable teal gradient button (replaced inline styles in 22 files)
  - Encapsulates `linear-gradient(135deg, #0D7377 0%, #14A3A8 100%)` pattern
  - Defaults: `variant="contained"`, `size="small"`
  - Used across all measure, builder, CDS, FHIR, terminology, and execution components

- [x] **4.1** `StatusChip.tsx` — Consistent measure status display
  - Variant-colored outlined chip with icon (Draft/Active/Retired)
  - Replaces ad-hoc Chip with manual color ternaries

- [x] **4.1** `SectionHeader.tsx` — Reusable panel header
  - Title + optional HelpTooltip + optional action buttons
  - Used in MeasureDetailsTab, extensible to other sections

- [x] **4.2** Added `useUnsavedChangesGuard` to `MeasureCqlTab.tsx` and `TestCaseEditor.tsx`
  - MeasureCqlTab: guards based on `cqlContent !== measure.cqlContent`
  - TestCaseEditor: tracks `isDirty` via title/description/bundle/population changes

- [x] **4.3** `WorkflowIndicator.tsx` — Step-based workflow progress
  - 4 steps: Details, CQL, Populations, Active
  - Checks measure completeness with green/gray icons + connecting lines
  - Integrated into MeasureEditor header alongside StatusChip

- [x] **4.4** Expanded `helpContent.ts` with measure tab help text
  - Added: details, cql, populationCriteria, evaluate, testCases, reports
  - HelpTooltip added to: MeasureDetailsTab, PopulationCriteriaTab, MeasureEvaluationTab, TestCasesTab

### Architecture (Completed)

```
components/common/
├── GradientButton.tsx (replaces 22 inline gradient patterns)
├── StatusChip.tsx (draft/active/retired with icons)
├── SectionHeader.tsx (title + help + actions)
└── HelpTooltip.tsx (existing, now used in measure tabs)

components/measure/
├── WorkflowIndicator.tsx (4-step progress: Details→CQL→Populations→Active)
└── MeasureEditor.tsx (header: title + StatusChip + WorkflowIndicator)

hooks/
└── useUnsavedChangesGuard.ts (now used in 4 components: Details, CQL, PopCriteria, TestCaseEditor)

constants/
└── helpContent.ts (expanded with 6 measure help entries)
```
