# CQL Builder Enhancement Checklist

## P0 - Critical

### Edit/Delete Existing Elements
- [ ] Add edit (pencil) icon to each parsed element in all 6 sections
- [ ] Add delete (trash) icon with confirmation dialog
- [ ] Add go-to (click element name → jump to line in Monaco)
- [ ] Edit: pre-fill form with parsed values, replace in-place via regex location
- [ ] Delete: remove corresponding CQL line(s) from editor content
- [ ] Sections: Includes, ValueSets, Codes, Parameters, Definitions, Functions

### Retrieve Expression Builder
- [ ] FHIR Resource type dropdown (Observation, Condition, Procedure, Encounter, etc.)
- [ ] Terminology binding picker (select from declared valueSets/codes)
- [ ] Generate `[ResourceType: "TerminologyBinding"]` syntax
- [ ] Chain common patterns: lookback, most recent, verified, active filters
- [ ] Add as new mode in DefinitionsSection (Template → Retrieve → Freeform)

## P1 - High Value

### Monaco IntelliSense Enhancement
- [ ] Autocomplete declared names: valueSets, codes, definitions, functions, parameters
- [ ] FHIR attribute completion: `Condition.` → `code`, `clinicalStatus`, `onset`...
  - Reuse backend `FhirStructureDefinitionService` (already has element introspection)
- [ ] Function signature hints on hover
- [ ] Type info hover on definitions (from translate API resultType)
- [ ] Register Monaco `CompletionItemProvider` for `cql` language

### Snippet Preview & Copy
- [ ] Preview pane (monospace, read-only) above Insert button showing generated CQL
- [ ] Copy to Clipboard button on preview
- [ ] Post-insert highlight: Monaco decoration on new lines (2s fade)
- [ ] Apply to all 6 sections

### Query Clause Builder
- [ ] Source picker: select retrieve expression or existing definition
- [ ] Alias auto-suggestion (first letter of resource type)
- [ ] Where clause builder: attribute + operator + value (visual rows)
- [ ] With/Without clause: related source + such that condition
- [ ] Return clause: select fields to project
- [ ] Sort clause: field + asc/desc
- [ ] Live CQL preview of assembled query
- [ ] Add as mode in DefinitionsSection alongside Retrieve Builder

## P2 - Completeness

### Concept Declaration Builder
- [ ] Concept name text field
- [ ] Multi-select from declared codes (checkbox list)
- [ ] Generate `concept "Name": { "Code1", "Code2" }` syntax
- [ ] Add as sub-section under Codes accordion

### Operator Palette
- [ ] Accordion section with categorized operators:
  - Comparison: `=`, `!=`, `~`, `!~`, `<`, `>`, `between`
  - Interval: `contains`, `in`, `overlaps`, `before`, `after`, `meets`, `starts`, `ends`
  - Aggregate: `Count`, `Sum`, `Min`, `Max`, `Avg`, `Median`
  - Type: `as`, `is`, `ToString`, `ToDateTime`, `ToQuantity`
  - Null: `is null`, `is not null`, `Coalesce`
  - DateTime: `Now()`, `Today()`, `date from`, `duration in`
- [ ] Click-to-insert snippet at cursor
- [ ] Search/filter within operator list

## P3 - Nice to Have

### Context Switcher
- [ ] Dropdown in Builder header: Patient (default), Practitioner, Unfiltered
- [ ] Auto-update `context` line in CQL on change

---

## Implementation Notes

- Builder files: `frontend/src/components/builder/`
- Hook: `frontend/src/hooks/useCqlStructure.ts` (parses CQL → structure)
- Redux: `editorSlice.ts` (`cqlContent`, `cursorPosition`, `setCqlContent`)
- Monaco language: `frontend/src/utils/cqlSyntax.ts`
- Backend FHIR introspection: `FhirStructureDefinitionService` (element metadata)
- Insertion pipeline: `EditorPage.handleInsertSnippet()` → splice at cursor line
- For edit/delete: need line-level mapping from parsed elements back to source positions
