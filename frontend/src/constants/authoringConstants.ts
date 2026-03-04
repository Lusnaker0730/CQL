/**
 * Centralized constants for the CDS Authoring Tool.
 * Single source of truth shared across ArtifactWorkspace, ImportCqlDialog,
 * Recommendations, and other authoring components.
 */

// --- CQL system definition names (must match backend AuthoringConstants) ---

export const DEF_MEETS_INCLUSION = 'MeetsInclusionCriteria'
export const DEF_MEETS_EXCLUSION = 'MeetsExclusionCriteria'
export const DEF_IN_POPULATION = 'InPopulation'
export const DEF_RECOMMENDATION = 'Recommendation'
export const DEF_ERRORS = 'Errors'

/** Set of all system definitions that should NOT be treated as user elements */
export const SYSTEM_DEFINITIONS = new Set([
  DEF_MEETS_INCLUSION, DEF_MEETS_EXCLUSION, DEF_IN_POPULATION,
  DEF_RECOMMENDATION, DEF_ERRORS, 'Patient',
])

// --- Special subpopulation IDs (must match backend AuthoringConstants) ---

export const SUBPOP_DOESNT_MEET_INCLUSION = '__doesnt_meet_inclusion__'
export const SUBPOP_MEETS_EXCLUSION = '__meets_exclusion__'

export const SPECIAL_SUBPOPS = [
  { uniqueId: SUBPOP_DOESNT_MEET_INCLUSION, subpopulationName: "Doesn't Meet Inclusion Criteria", special: true as const },
  { uniqueId: SUBPOP_MEETS_EXCLUSION, subpopulationName: 'Meets Exclusion Criteria', special: true as const },
]

// --- FHIR version options (must match backend AuthoringConstants.FHIR_VERSION_MAP) ---

// Authoring currently supports R4 only
export const FHIR_VERSION_OPTIONS = [
  { key: 'R4', label: 'R4 (4.0.1)' },
] as const

// --- Default artifact values ---

export const DEFAULT_VERSION = '1.0.0'
export const DEFAULT_STATUS = 'draft'
export const DEFAULT_FHIR_VERSION = '4.0.1'

// --- Visual builder element type colors ---

/** Color map for return types in the expression tree visual builder */
export const RETURN_TYPE_COLORS: Record<string, string> = {
  boolean: '#2196F3',       // blue – boolean results
  list_of_conditions: '#4CAF50',
  list_of_observations: '#4CAF50',
  list_of_medications: '#4CAF50',
  list_of_procedures: '#4CAF50',
  list_of_encounters: '#4CAF50',
  list_of_immunizations: '#4CAF50',
  list_of_allergy_intolerances: '#4CAF50',
  list_of_devices: '#4CAF50',
  list_of_service_requests: '#4CAF50',
  list_of_medication_statements: '#4CAF50',
  list_of_medication_requests: '#4CAF50',
  list_of_any: '#4CAF50',   // green – list types
  observation: '#FF9800',
  condition: '#FF9800',
  procedure: '#FF9800',      // orange – single resources
  integer: '#9C27B0',
  decimal: '#9C27B0',
  system_quantity: '#9C27B0', // purple – numeric types
  string: '#795548',          // brown – string
}

export const RETURN_TYPE_COLOR_DEFAULT = '#757575'

/** Background tints for special element reference types */
export const ELEMENT_REF_BACKGROUNDS: Record<string, string> = {
  baseElementRef: '#E3F2FD',
  parameterRef: '#F3E5F5',
  externalCqlRef: '#E8F5E9',
}

// --- Conjunction (And/Or) colors ---

/** Color for AND conjunction borders/chips (matches theme primary.main) */
export const CONJUNCTION_COLOR_AND = '#0D7377'
/** Color for OR conjunction borders/chips (orange accent) */
export const CONJUNCTION_COLOR_OR = '#E67E22'

// --- Code block styling ---

/** Reusable sx for code preview blocks (dark bg in dark mode, light grey in light) */
export const codeBlockSx = {
  p: 1.5,
  borderRadius: 1,
  backgroundColor: (theme: { palette: { mode: string } }) =>
    theme.palette.mode === 'dark' ? '#1e1e1e' : '#f5f5f5',
  fontFamily: '"Fira Code", "Consolas", monospace',
  fontSize: '0.85rem',
  whiteSpace: 'pre-wrap' as const,
  lineHeight: 1.6,
  border: 1,
  borderColor: 'divider',
} as const

// --- Keyboard shortcuts ---

/** Tab index for the "Review CQL" tab in ArtifactWorkspace */
export const TAB_INDEX_REVIEW_CQL = 8
/** Tab index for the "Summary" tab in ArtifactWorkspace */
export const TAB_INDEX_SUMMARY = 10

/** Shortcut definitions displayed in the help dialog and used by the key handler */
export const KEYBOARD_SHORTCUTS = [
  { key: 'Ctrl + S', description: 'Save artifact' },
  { key: 'Ctrl + Z', description: 'Undo last change' },
  { key: 'Ctrl + Y', description: 'Redo last change' },
  { key: 'Ctrl + G', description: 'Go to Review CQL' },
  { key: 'Ctrl + 1–9', description: 'Switch to tab 1–9' },
  { key: 'Ctrl + 0', description: 'Switch to Summary' },
  { key: 'Ctrl + /', description: 'Toggle this help' },
] as const
