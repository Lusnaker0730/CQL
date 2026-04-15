/**
 * Single source of truth for CDS Hooks constants.
 * Keep in sync with backend HookTypeValidator.VALID_HOOKS and HookContextRequirements.
 */

export interface CdsContextField {
  name: string
  required: boolean
  type: 'string' | 'object'
}

export interface CdsHookType {
  id: string
  description: string
  requiredContext: CdsContextField[]
}

export const CDS_HOOK_TYPES: CdsHookType[] = [
  {
    id: 'patient-view',
    description: 'Triggered when a patient chart is opened',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
    ],
  },
  {
    id: 'order-select',
    description: 'Triggered when a clinician selects orders (e.g., medications, labs)',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
      { name: 'selections', required: true, type: 'object' },
      { name: 'draftOrders', required: true, type: 'object' },
    ],
  },
  {
    id: 'order-sign',
    description: 'Triggered when a clinician is about to sign orders',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
      { name: 'draftOrders', required: true, type: 'object' },
    ],
  },
  {
    id: 'appointment-book',
    description: 'Triggered when an appointment is being booked',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
      { name: 'appointments', required: true, type: 'object' },
    ],
  },
  {
    id: 'encounter-start',
    description: 'Triggered when a new encounter begins',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
      { name: 'encounterId', required: true, type: 'string' },
    ],
  },
  {
    id: 'encounter-discharge',
    description: 'Triggered when a patient is being discharged',
    requiredContext: [
      { name: 'userId', required: true, type: 'string' },
      { name: 'patientId', required: true, type: 'string' },
      { name: 'encounterId', required: true, type: 'string' },
    ],
  },
]

export const CDS_HOOK_IDS = CDS_HOOK_TYPES.map((h) => h.id)

/**
 * Returns all required context fields for a given hook type.
 */
export function getRequiredContextFields(hookId: string): CdsContextField[] {
  return CDS_HOOK_TYPES.find((h) => h.id === hookId)?.requiredContext ?? []
}

/**
 * Returns only the string-type context fields (rendered as text inputs).
 */
export function getStringContextFields(hookId: string): CdsContextField[] {
  return getRequiredContextFields(hookId).filter((f) => f.type === 'string')
}

/**
 * Returns only the object-type context fields (rendered as JSON editors).
 */
export function getObjectContextFields(hookId: string): CdsContextField[] {
  return getRequiredContextFields(hookId).filter((f) => f.type === 'object')
}

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
