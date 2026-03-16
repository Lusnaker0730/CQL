/**
 * Single source of truth for CDS Hooks constants.
 * Keep in sync with backend HookTypeValidator.VALID_HOOKS.
 */

export interface CdsHookType {
  id: string
  description: string
}

export const CDS_HOOK_TYPES: CdsHookType[] = [
  { id: 'patient-view', description: 'Triggered when a patient chart is opened' },
  { id: 'order-select', description: 'Triggered when a clinician selects orders (e.g., medications, labs)' },
  { id: 'order-sign', description: 'Triggered when a clinician is about to sign orders' },
  { id: 'appointment-book', description: 'Triggered when an appointment is being booked' },
  { id: 'encounter-start', description: 'Triggered when a new encounter begins' },
  { id: 'encounter-discharge', description: 'Triggered when a patient is being discharged' },
]

export const CDS_HOOK_IDS = CDS_HOOK_TYPES.map((h) => h.id)

export const CDS_INDICATOR_TYPES = ['info', 'warning', 'critical'] as const

export type CdsIndicatorType = (typeof CDS_INDICATOR_TYPES)[number]

export function getHookDescription(hookId: string): string | undefined {
  return CDS_HOOK_TYPES.find((h) => h.id === hookId)?.description
}

export function isValidHookType(value: string): boolean {
  return CDS_HOOK_IDS.includes(value)
}

// --- CDS Card options for Recommendations ---

export const CDS_SELECTION_BEHAVIORS = [
  { value: '', label: '(None)' },
  { value: 'at-most-one', label: 'At Most One' },
  { value: 'any', label: 'Any' },
] as const

export const CDS_ACTION_RESOURCE_TYPES = [
  { value: 'MedicationRequest', label: 'MedicationRequest' },
  { value: 'ServiceRequest', label: 'ServiceRequest' },
] as const

export const CDS_LINK_TYPES = [
  { value: 'absolute', label: 'Link' },
  { value: 'smart', label: 'SMART App' },
] as const

export const DEFAULT_INDICATOR: CdsIndicatorType = 'info'

// --- Indicator color helper ---

export function getIndicatorColor(indicator: string): 'error' | 'warning' | 'info' {
  switch (indicator) {
    case 'critical':
      return 'error'
    case 'warning':
      return 'warning'
    case 'info':
    default:
      return 'info'
  }
}

// --- Feedback outcomes (keep in sync with backend CdsConstants) ---

export const FEEDBACK_ACCEPTED = 'accepted' as const
export const FEEDBACK_OVERRIDDEN = 'overridden' as const
export const FEEDBACK_OVERRIDE_CODE = 'override' as const
export const FEEDBACK_OVERRIDE_DEFAULT_DISPLAY = 'No reason provided'
