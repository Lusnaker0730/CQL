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

export const FHIR_VERSION_OPTIONS = [
  { key: 'R4', label: 'R4 (4.0.1)' },
  { key: 'STU3', label: 'STU3 (3.0.2)' },
  { key: 'DSTU2', label: 'DSTU2 (1.0.2)' },
] as const

// --- Default artifact values ---

export const DEFAULT_VERSION = '1.0.0'
export const DEFAULT_STATUS = 'draft'
export const DEFAULT_FHIR_VERSION = '4.0.1'
