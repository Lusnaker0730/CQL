# CDS Authoring Tool — UX Optimization Roadmap

> Created: 2026-02-11
> Status: Phase A + B + C Complete

---

## Overview

This roadmap addresses UX issues identified in the CDS Authoring Tool (Phases 6-12).
Organized into 4 phases by priority: safety → guidance → efficiency → completeness.

---

## Phase A — Safety Guards (Prevent Data Loss)

**Goal**: Ensure users never accidentally lose work.

### A1: Unsaved Changes Guard ✅
- [x] Import and apply `useUnsavedChangesGuard` in `ArtifactWorkspace.tsx`
- [x] Hook into `isDirty` state to trigger browser `beforeunload` warning
- [x] Show confirmation dialog on tab/page navigation when dirty
- [x] Verify guard clears on successful save
- **Files**: `ArtifactWorkspace.tsx`

### A2: Delete Confirmations ✅
- [x] `Subpopulations.tsx` — add confirmation dialog before removing a subpopulation
- [x] `BaseElements.tsx` — add confirmation dialog before removing a base element
- [x] `Parameters.tsx` — add confirmation dialog before removing a parameter
- [x] `Recommendations.tsx` — add confirmation dialog before removing a recommendation
- [x] Use consistent dialog pattern (title, description with item name, Cancel/Delete buttons)
- [x] Delete button should be red/destructive styled
- **Files**: `Subpopulations.tsx`, `BaseElements.tsx`, `Parameters.tsx`, `Recommendations.tsx`

### A3: Form Validation ✅
- [x] `Parameters.tsx` — validate name non-empty, no duplicates, type required
- [ ] `Parameters.tsx` — type-specific default value validation (integer must be number, etc.)
- [x] `Recommendations.tsx` — validate recommendation text non-empty
- [x] `Subpopulations.tsx` — validate subpopulation name non-empty, no duplicates
- [x] `BaseElements.tsx` — validate base element name non-empty
- [ ] `ErrorStatement` — validate no duplicate conditions
- [x] Show inline error messages (MUI `helperText` + `error` prop)
- [ ] Block save if critical validation errors exist (or warn user)
- **Files**: `Parameters.tsx`, `Recommendations.tsx`, `Subpopulations.tsx`, `BaseElements.tsx`

### A4: Modifier Compatibility Check ✅
- [x] In `ArtifactElement.tsx`, validate modifier chain input/output types match
- [x] Disable incompatible modifiers in modifier selection UI (already filtered by currentReturnType)
- [x] Show warning chip on elements with broken modifier chains
- [x] Auto-clean incompatible modifiers on removal from chain
- **Files**: `ArtifactElement.tsx`, `ArtifactElementBody.tsx`

---

## Phase B — Guidance & Feedback (Lower Learning Curve)

**Goal**: New users can get started quickly; errors are self-diagnosable.

### B1: Structured Error Messages ✅
- [x] `ArtifactWorkspaceHeader.tsx` — structured deploy/save/CQL errors with title + message + suggestion
- [x] `CqlPreviewPanel.tsx` — numbered validation errors with guidance text
- [x] `ExternalCql.tsx` — upload errors with file format hints
- [x] `ImportCqlDialog.tsx` — parse errors with all errors shown (not truncated to 5) + guidance
- [x] `ArtifactTester.tsx` — test failure error with troubleshooting hints
- **Files**: Header, CqlPreviewPanel, ExternalCql, ImportCqlDialog, ArtifactTester

### B2: Inline Help Text ✅
- [x] `Parameters.tsx` — each parameter type has a hint description shown in dropdown and tooltip
- [x] Deploy dialog — hook type helperText changes dynamically based on selection
- [x] `ArtifactTester.tsx` — FHIR server URL helperText explaining format and accessibility
- [x] `Recommendations.tsx` — help icon with tooltip explaining all grade levels
- [x] `ElementSelectDropdown.tsx` — context-specific descriptions for each template element
- **Files**: `Parameters.tsx`, `ArtifactWorkspaceHeader.tsx`, `ArtifactTester.tsx`, `Recommendations.tsx`, `ElementSelectDropdown.tsx`

### B3: Empty State Guidance ✅
- [x] `ConjunctionGroup` — enhanced empty state with icons and context-specific guidance for Inclusions/Exclusions
- [x] Other tabs already had good empty states (Subpopulations, BaseElements, Recommendations, Parameters, ErrorStatement)
- **Files**: `ConjunctionGroup.tsx`

### B4: CQL Sync Status Indicator ✅
- [x] Track `cqlIsStale` state (set when isDirty changes after CQL was generated)
- [x] Show "CQL is outdated" warning banner in Review CQL tab when stale
- [x] Auto-clear stale flag on successful CQL generation
- **Files**: `CqlPreviewPanel.tsx`

### B5: Loading Skeleton ✅
- [x] Add skeleton UI for ArtifactWorkspace initial load (header + tabs + content)
- [x] Add skeleton table rows for ArtifactList while loading (replaces CircularProgress)
- **Files**: `AuthoringPage.tsx`, `ArtifactList.tsx`

---

## Phase C — Efficiency Boost (Power User Fluency)

**Goal**: Experienced users can work faster with fewer clicks.

### C1: Logic Tree Search ✅
- [x] Add search input above ConjunctionGroup in Inclusions/Exclusions tabs (appears at 3+ elements)
- [x] Filter matching elements by name, template type, or return type
- [x] Recursive matching — conjunction groups shown if any child matches
- [x] Clear button and "X of Y shown" filter indicator chip
- **Files**: `ConjunctionGroup.tsx`

### C2: Keyboard Shortcuts Extension ✅
- [x] Ctrl+G — go to Review CQL tab (from any tab)
- [x] Ctrl+Z — undo, Ctrl+Y — redo (integrated with C4)
- [x] Ctrl+1 through Ctrl+0 — switch to tab 1–9, Ctrl+0 = Summary
- [x] Ctrl+/ — toggle keyboard shortcut help dialog
- [x] Shortcuts disabled in input/textarea focus to avoid conflicts
- **Files**: `ArtifactWorkspace.tsx`

### C3: Batch Operations
- [ ] Multi-select elements in ConjunctionGroup (checkbox on each element)
- [ ] Batch actions toolbar: Delete Selected, Move to Group, Copy to Clipboard
- [ ] Drag-and-drop reorder within and between conjunction groups
- **Files**: `ConjunctionGroup.tsx`, `ArtifactElement.tsx`

### C4: Undo/Redo ✅
- [x] Implement artifact state history stack (useRef with past/future arrays)
- [x] Ctrl+Z — undo last artifact change
- [x] Ctrl+Y — redo
- [x] History capped at 50 states to limit memory usage
- [x] Reset history on artifact switch
- **Files**: New `useArtifactHistory.ts` hook, `ArtifactWorkspace.tsx`

### C5: Performance Optimization ✅
- [x] Wrap `ConjunctionGroup` with `React.memo`
- [x] Wrap `ArtifactElement` with `React.memo`
- [x] Memoize filtered children with `useMemo` in ConjunctionGroup
- **Files**: `ConjunctionGroup.tsx`, `ArtifactElement.tsx`

---

## Phase D — Completeness (Enterprise Quality)

**Goal**: Meet production-grade standards for accessibility, responsiveness, and auditability.

### D1: Accessibility (a11y)
- [ ] Add `aria-label` to all icon buttons (collapse, delete, move, add)
- [ ] Add `aria-expanded` to collapsible sections
- [ ] Keyboard navigation: Tab through elements, Enter to expand, Space to select
- [ ] AND/OR visual distinction: add icon/pattern supplement to color coding
- [ ] Screen reader announcements for tree mutations (element added/removed)
- [ ] Test with axe-core or Lighthouse accessibility audit
- **Files**: All builder/ components, ConjunctionGroup, ArtifactElement

### D2: Responsive Layout
- [ ] ArtifactWorkspaceHeader — collapse buttons into overflow menu on narrow screens
- [ ] Tab bar — horizontal scroll with arrow indicators on mobile
- [ ] Tables (ArtifactList, test results) — horizontal scroll wrapper
- [ ] Dialog modals — full-screen on mobile breakpoint
- [ ] Test at 768px, 1024px, 1440px breakpoints
- **Files**: Header, ArtifactWorkspace, ArtifactList, all dialog components

### D3: Version History
- [ ] Backend: new `artifact_version_history` table (artifact_id, version, snapshot_json, timestamp, username)
- [ ] Backend: save snapshot on every update (or on explicit "create version")
- [ ] Frontend: version history panel/dialog showing timeline of changes
- [ ] Ability to view/compare/restore previous versions
- [ ] Show version badge in workspace header
- **Files**: New entity, repository, service, controller endpoint, frontend component

### D4: Auto-Save
- [ ] Debounced auto-save (e.g., 30 seconds after last edit)
- [ ] Visual indicator: "Saving..." / "All changes saved" / "Save failed"
- [ ] Conflict detection: if server version is newer, show merge prompt
- [ ] Disable auto-save when validation errors exist
- [ ] User preference toggle to enable/disable auto-save
- **Files**: `ArtifactWorkspace.tsx`, new `useAutoSave.ts` hook

---

## Implementation Order

```
Week 1-2:  Phase A (Safety Guards)
           A1 → A2 → A3 → A4

Week 3-4:  Phase B (Guidance & Feedback)
           B5 → B3 → B2 → B1 → B4

Week 5-6:  Phase C (Efficiency)
           C5 → C1 → C2 → C4 → C3

Week 7+:   Phase D (Completeness)
           D1 → D2 → D4 → D3
```

---

## File Impact Summary

| Phase | Files Changed | New Files | Risk |
|-------|--------------|-----------|------|
| A | ~10 | 0 | Low — small edits to existing components |
| B | ~12 | 1-2 | Low — additive changes, no logic refactor |
| C | ~6 | 2-3 | Medium — state management changes |
| D | ~15+ | 3-5 | Medium-High — backend schema + cross-cutting |

---

## Success Metrics

- **Phase A**: Zero reports of accidental data loss; all destructive actions require confirmation
- **Phase B**: New user can create and test an artifact without external documentation
- **Phase C**: Power user can perform common operations 50% faster (measured by click count)
- **Phase D**: Lighthouse accessibility score ≥ 90; usable on tablet (1024px)
