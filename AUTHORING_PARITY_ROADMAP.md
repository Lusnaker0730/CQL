# CDS Authoring Tool - AHRQ Parity Roadmap

## Overview
Gap analysis between AHRQ CDS Authoring Tool User Guide and our implementation.
12 gaps identified, organized into 6 phases.

## Phase A: Tab Status Indicators + Default Subpopulations (LOW → quick wins)
1. **Tab Content/Error Indicators** - Checkmark icon on tabs with content, exclamation on tabs with errors
2. **Default Special Subpopulations** - Create "Doesn't Meet Inclusion Criteria" and "Meets Exclusion Criteria" as special subpopulations, selectable in Recommendations

## Phase B: Cross-Referencing (Base Elements + Parameters + External CQL)
3. **Base Element References** - Dynamically populate "Base Elements" category in element selector with existing base elements from the artifact; light-blue shading, link navigation, usage backlinks, deletion prevention
4. **Parameter References** - Dynamically populate "Parameters" category with existing parameters from the artifact; usable in Inclusions/Exclusions/Subpopulations/Error Handling
5. **External CQL Element Usage** - Dynamically populate "External CQL" category with definitions/parameters/functions from uploaded libraries; function argument editors

## Phase C: External CQL Modifiers + FHIR Version Locking
6. **External CQL as Modifiers** - External CQL functions whose first arg matches element return type appear in modifier selector
7. **FHIR Version Locking** - Validate uploaded external CQL FHIR version against artifact; prevent incompatible uploads

## Phase D: Custom Modifier Builder
8. **Build New Modifier** - Rule-based modifier builder: FHIR field property selector, operator selector, value input, nested And/Or groups, validation

## Phase E: Recommendation Suggestions + CPG Metadata
9. **Suggestion Actions** - UI for adding Suggestions to Recommendations, each with Actions (create MedicationRequest/ServiceRequest)
10. **CPG Metadata Editor** - "Show CPG Fields" button revealing form for all CPG publishable library metadata with completion progress bar

## Phase F: Indent/Outdent + FHIR Version Selection
11. **Indent/Outdent Buttons** - One-click indent element into new nested group, outdent back to parent level
12. **FHIR Version Selection** - Version picker when viewing/downloading CQL (DSTU2/STU3/R4)

## Status Tracking
| # | Feature | Phase | Status |
|---|---------|-------|--------|
| 1 | Tab indicators | A | Done |
| 2 | Default subpopulations | A | Done |
| 3 | Base element refs | B | Done |
| 4 | Parameter refs | B | Done |
| 5 | External CQL elements | B | Done |
| 6 | External CQL modifiers | C | Done |
| 7 | FHIR version locking | C | Done |
| 8 | Custom modifier builder | D | Done |
| 9 | Suggestion actions | E | Done |
| 10 | CPG metadata editor | E | Done |
| 11 | Indent/outdent | F | Done |
| 12 | FHIR version selection | F | Done |
