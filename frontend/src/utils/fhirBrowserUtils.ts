/** A loosely-typed FHIR resource with required resourceType. */
export interface FhirResource extends Record<string, unknown> {
  resourceType: string
  id?: string
}

/**
 * Safely traverse a dot-separated path on an unknown object,
 * returning the value at that path or undefined.
 * Supports bracket-free array indexing via numeric segments (e.g. "name.0.family").
 */
function getNestedValue(obj: unknown, path: string): unknown {
  let current: unknown = obj
  for (const segment of path.split('.')) {
    if (current == null || typeof current !== 'object') return undefined
    const idx = Number(segment)
    if (Array.isArray(current) && !Number.isNaN(idx)) {
      current = current[idx]
    } else {
      current = (current as Record<string, unknown>)[segment]
    }
  }
  return current
}

/** Shorthand: resolve a nested path to a string, falling back to ''. */
function str(resource: FhirResource, path: string): string {
  const v = getNestedValue(resource, path)
  return typeof v === 'string' ? v : ''
}

/** Shorthand: resolve a CodeableConcept-style path to its display or text. */
function codeDisplay(resource: FhirResource, basePath: string): string {
  return str(resource, `${basePath}.coding.0.display`) || str(resource, `${basePath}.text`)
}

/** Format a HumanName at the given array path (e.g. "name.0"). */
function humanName(resource: FhirResource, path: string): string {
  const nameObj = getNestedValue(resource, path)
  if (!nameObj || typeof nameObj !== 'object') return ''
  const n = nameObj as Record<string, unknown>
  const family = typeof n.family === 'string' ? n.family : ''
  const given = Array.isArray(n.given) ? (n.given as string[]) : []
  return [family, ...given].filter(Boolean).join(', ')
}

export function formatJson(data: unknown): string {
  try {
    if (typeof data === 'string') {
      return JSON.stringify(JSON.parse(data), null, 2)
    }
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

export function getResourceCount(data: unknown): number {
  if (!data) return 0
  try {
    const parsed = typeof data === 'string' ? JSON.parse(data) : data
    if (parsed.total !== undefined) return parsed.total
    if (parsed.entry) return parsed.entry.length
    return 0
  } catch {
    return 0
  }
}

export interface DisplayField {
  key: string
  label: string
  extract: (r: FhirResource) => string
}

export const RESOURCE_DISPLAY_FIELDS: Record<string, DisplayField[]> = {
  Patient: [
    { key: 'name', label: 'Name', extract: r => humanName(r, 'name.0') },
    { key: 'gender', label: 'Gender', extract: r => str(r, 'gender') },
    { key: 'birthDate', label: 'Birth Date', extract: r => str(r, 'birthDate') },
  ],
  Encounter: [
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'class', label: 'Class', extract: r => str(r, 'class.code') || str(r, 'class.display') },
    { key: 'period', label: 'Period', extract: r => {
      const start = str(r, 'period.start')
      return start ? `${start} — ${str(r, 'period.end')}` : ''
    }},
  ],
  Condition: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'clinicalStatus', label: 'Clinical Status', extract: r => str(r, 'clinicalStatus.coding.0.code') },
    { key: 'onset', label: 'Onset', extract: r => str(r, 'onsetDateTime') },
  ],
  Observation: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'date', label: 'Date', extract: r => str(r, 'effectiveDateTime') || str(r, 'effectivePeriod.start') },
  ],
  Procedure: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'date', label: 'Date', extract: r => str(r, 'performedDateTime') || str(r, 'performedPeriod.start') },
  ],
  MedicationRequest: [
    { key: 'medication', label: 'Medication', extract: r =>
      codeDisplay(r, 'medicationCodeableConcept') || str(r, 'medicationReference.display')
    },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'intent', label: 'Intent', extract: r => str(r, 'intent') },
  ],
  MedicationStatement: [
    { key: 'medication', label: 'Medication', extract: r => codeDisplay(r, 'medicationCodeableConcept') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'date', label: 'Date', extract: r => str(r, 'effectiveDateTime') || str(r, 'effectivePeriod.start') },
  ],
  DiagnosticReport: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'date', label: 'Date', extract: r => str(r, 'effectiveDateTime') || str(r, 'effectivePeriod.start') },
  ],
  ServiceRequest: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'intent', label: 'Intent', extract: r => str(r, 'intent') },
  ],
  Immunization: [
    { key: 'vaccine', label: 'Vaccine', extract: r => codeDisplay(r, 'vaccineCode') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'date', label: 'Date', extract: r => str(r, 'occurrenceDateTime') },
  ],
  AllergyIntolerance: [
    { key: 'code', label: 'Code', extract: r => codeDisplay(r, 'code') },
    { key: 'clinicalStatus', label: 'Clinical Status', extract: r => str(r, 'clinicalStatus.coding.0.code') },
    { key: 'type', label: 'Type', extract: r => str(r, 'type') },
  ],
  CarePlan: [
    { key: 'title', label: 'Title', extract: r => str(r, 'title') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'intent', label: 'Intent', extract: r => str(r, 'intent') },
  ],
  Goal: [
    { key: 'description', label: 'Description', extract: r => str(r, 'description.text') },
    { key: 'lifecycleStatus', label: 'Status', extract: r => str(r, 'lifecycleStatus') },
    { key: 'target', label: 'Target Date', extract: r => str(r, 'target.0.dueDate') },
  ],
  Claim: [
    { key: 'type', label: 'Type', extract: r =>
      str(r, 'type.coding.0.display') || str(r, 'type.coding.0.code')
    },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'total', label: 'Total', extract: r => {
      const value = getNestedValue(r, 'total.value')
      if (value == null) return ''
      const currency = str(r, 'total.currency')
      return `${value} ${currency}`.trim()
    }},
  ],
  Coverage: [
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'type', label: 'Type', extract: r => codeDisplay(r, 'type') },
    { key: 'period', label: 'Period', extract: r => {
      const start = str(r, 'period.start')
      return start ? `${start} — ${str(r, 'period.end')}` : ''
    }},
  ],
  Organization: [
    { key: 'name', label: 'Name', extract: r => str(r, 'name') },
    { key: 'type', label: 'Type', extract: r => str(r, 'type.0.coding.0.display') },
    { key: 'active', label: 'Active', extract: r => {
      const active = getNestedValue(r, 'active')
      return active !== undefined ? String(active) : ''
    }},
  ],
  Practitioner: [
    { key: 'name', label: 'Name', extract: r => humanName(r, 'name.0') },
    { key: 'gender', label: 'Gender', extract: r => str(r, 'gender') },
    { key: 'active', label: 'Active', extract: r => {
      const active = getNestedValue(r, 'active')
      return active !== undefined ? String(active) : ''
    }},
  ],
  Location: [
    { key: 'name', label: 'Name', extract: r => str(r, 'name') },
    { key: 'status', label: 'Status', extract: r => str(r, 'status') },
    { key: 'type', label: 'Type', extract: r => str(r, 'type.0.coding.0.display') },
  ],
}

const FALLBACK_FIELDS: DisplayField[] = [
  { key: 'lastUpdated', label: 'Last Updated', extract: r => str(r, 'meta.lastUpdated') },
]

/** Derived from RESOURCE_DISPLAY_FIELDS keys — single source of truth for the browser dropdown. */
export const FHIR_RESOURCE_TYPES = Object.keys(RESOURCE_DISPLAY_FIELDS)

export function getDisplayFields(resourceType: string): DisplayField[] {
  return RESOURCE_DISPLAY_FIELDS[resourceType] || FALLBACK_FIELDS
}

export function extractPaginationLinks(bundle: Record<string, unknown>): { next?: string; prev?: string } {
  const links = bundle.link as Array<{ relation: string; url: string }> | undefined
  if (!links) return {}
  const next = links.find(l => l.relation === 'next')?.url
  const prev = links.find(l => l.relation === 'previous' || l.relation === 'prev')?.url
  return { next, prev }
}

export function extractSearchParamsFromUrl(url: string): { resourceType: string; params: string } {
  try {
    const u = new URL(url)
    const pathParts = u.pathname.split('/').filter(Boolean)
    const resourceType = pathParts[pathParts.length - 1] || 'Patient'
    const params = u.search ? u.search.substring(1) : ''
    return { resourceType, params }
  } catch {
    return { resourceType: 'Patient', params: '' }
  }
}

export const RESOURCE_SEARCH_PARAMS: Record<string, { name: string; type: string; label: string }[]> = {
  Patient: [
    { name: 'name', type: 'string', label: 'Name' },
    { name: 'family', type: 'string', label: 'Family Name' },
    { name: 'given', type: 'string', label: 'Given Name' },
    { name: 'birthdate', type: 'date', label: 'Birth Date' },
    { name: 'gender', type: 'token', label: 'Gender' },
    { name: 'identifier', type: 'token', label: 'Identifier' },
    { name: 'address', type: 'string', label: 'Address' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Encounter: [
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'class', type: 'token', label: 'Class' },
    { name: 'date', type: 'date', label: 'Date' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'type', type: 'token', label: 'Type' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Condition: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'clinical-status', type: 'token', label: 'Clinical Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'onset-date', type: 'date', label: 'Onset Date' },
    { name: 'category', type: 'token', label: 'Category' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Observation: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'date', type: 'date', label: 'Date' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'category', type: 'token', label: 'Category' },
    { name: 'value-quantity', type: 'quantity', label: 'Value Quantity' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Procedure: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'date', type: 'date', label: 'Date' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  MedicationRequest: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'intent', type: 'token', label: 'Intent' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'authoredon', type: 'date', label: 'Authored On' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  MedicationStatement: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'effective', type: 'date', label: 'Effective Date' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  DiagnosticReport: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'date', type: 'date', label: 'Date' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  ServiceRequest: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'intent', type: 'token', label: 'Intent' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Immunization: [
    { name: 'vaccine-code', type: 'token', label: 'Vaccine Code' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'date', type: 'date', label: 'Date' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  AllergyIntolerance: [
    { name: 'code', type: 'token', label: 'Code' },
    { name: 'clinical-status', type: 'token', label: 'Clinical Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'type', type: 'token', label: 'Type' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  CarePlan: [
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'intent', type: 'token', label: 'Intent' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'category', type: 'token', label: 'Category' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Goal: [
    { name: 'lifecycle-status', type: 'token', label: 'Lifecycle Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'target-date', type: 'date', label: 'Target Date' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Claim: [
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'created', type: 'date', label: 'Created' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Coverage: [
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'patient', type: 'reference', label: 'Patient' },
    { name: 'type', type: 'token', label: 'Type' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Organization: [
    { name: 'name', type: 'string', label: 'Name' },
    { name: 'type', type: 'token', label: 'Type' },
    { name: 'active', type: 'token', label: 'Active' },
    { name: 'address', type: 'string', label: 'Address' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Practitioner: [
    { name: 'name', type: 'string', label: 'Name' },
    { name: 'family', type: 'string', label: 'Family Name' },
    { name: 'given', type: 'string', label: 'Given Name' },
    { name: 'identifier', type: 'token', label: 'Identifier' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
  Location: [
    { name: 'name', type: 'string', label: 'Name' },
    { name: 'status', type: 'token', label: 'Status' },
    { name: 'type', type: 'token', label: 'Type' },
    { name: 'address', type: 'string', label: 'Address' },
    { name: '_id', type: 'token', label: 'ID' },
  ],
}
