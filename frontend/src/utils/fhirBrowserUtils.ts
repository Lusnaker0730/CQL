export const FHIR_RESOURCE_TYPES = [
  'Patient',
  'Encounter',
  'Condition',
  'Observation',
  'Procedure',
  'MedicationRequest',
  'MedicationStatement',
  'DiagnosticReport',
  'ServiceRequest',
  'Immunization',
  'AllergyIntolerance',
  'CarePlan',
  'Goal',
  'Claim',
  'Coverage',
  'Organization',
  'Practitioner',
  'Location',
]

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

/* eslint-disable @typescript-eslint/no-explicit-any */
export const RESOURCE_DISPLAY_FIELDS: Record<string, { key: string; label: string; extract: (r: any) => string }[]> = {
  Patient: [
    { key: 'name', label: 'Name', extract: r => {
      const n = r.name?.[0]
      if (!n) return ''
      return [n.family, ...(n.given || [])].filter(Boolean).join(', ')
    }},
    { key: 'gender', label: 'Gender', extract: r => r.gender || '' },
    { key: 'birthDate', label: 'Birth Date', extract: r => r.birthDate || '' },
  ],
  Encounter: [
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'class', label: 'Class', extract: r => r.class?.code || r.class?.display || '' },
    { key: 'period', label: 'Period', extract: r => r.period?.start ? `${r.period.start} — ${r.period.end || ''}` : '' },
  ],
  Condition: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'clinicalStatus', label: 'Clinical Status', extract: r => r.clinicalStatus?.coding?.[0]?.code || '' },
    { key: 'onset', label: 'Onset', extract: r => r.onsetDateTime || '' },
  ],
  Observation: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'date', label: 'Date', extract: r => r.effectiveDateTime || r.effectivePeriod?.start || '' },
  ],
  Procedure: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'date', label: 'Date', extract: r => r.performedDateTime || r.performedPeriod?.start || '' },
  ],
  MedicationRequest: [
    { key: 'medication', label: 'Medication', extract: r => r.medicationCodeableConcept?.coding?.[0]?.display || r.medicationCodeableConcept?.text || r.medicationReference?.display || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'intent', label: 'Intent', extract: r => r.intent || '' },
  ],
  MedicationStatement: [
    { key: 'medication', label: 'Medication', extract: r => r.medicationCodeableConcept?.coding?.[0]?.display || r.medicationCodeableConcept?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'date', label: 'Date', extract: r => r.effectiveDateTime || r.effectivePeriod?.start || '' },
  ],
  DiagnosticReport: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'date', label: 'Date', extract: r => r.effectiveDateTime || r.effectivePeriod?.start || '' },
  ],
  ServiceRequest: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'intent', label: 'Intent', extract: r => r.intent || '' },
  ],
  Immunization: [
    { key: 'vaccine', label: 'Vaccine', extract: r => r.vaccineCode?.coding?.[0]?.display || r.vaccineCode?.text || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'date', label: 'Date', extract: r => r.occurrenceDateTime || '' },
  ],
  AllergyIntolerance: [
    { key: 'code', label: 'Code', extract: r => r.code?.coding?.[0]?.display || r.code?.text || '' },
    { key: 'clinicalStatus', label: 'Clinical Status', extract: r => r.clinicalStatus?.coding?.[0]?.code || '' },
    { key: 'type', label: 'Type', extract: r => r.type || '' },
  ],
  CarePlan: [
    { key: 'title', label: 'Title', extract: r => r.title || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'intent', label: 'Intent', extract: r => r.intent || '' },
  ],
  Goal: [
    { key: 'description', label: 'Description', extract: r => r.description?.text || '' },
    { key: 'lifecycleStatus', label: 'Status', extract: r => r.lifecycleStatus || '' },
    { key: 'target', label: 'Target Date', extract: r => r.target?.[0]?.dueDate || '' },
  ],
  Claim: [
    { key: 'type', label: 'Type', extract: r => r.type?.coding?.[0]?.display || r.type?.coding?.[0]?.code || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'total', label: 'Total', extract: r => r.total?.value ? `${r.total.value} ${r.total.currency || ''}` : '' },
  ],
  Coverage: [
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'type', label: 'Type', extract: r => r.type?.coding?.[0]?.display || r.type?.text || '' },
    { key: 'period', label: 'Period', extract: r => r.period?.start ? `${r.period.start} — ${r.period.end || ''}` : '' },
  ],
  Organization: [
    { key: 'name', label: 'Name', extract: r => r.name || '' },
    { key: 'type', label: 'Type', extract: r => r.type?.[0]?.coding?.[0]?.display || '' },
    { key: 'active', label: 'Active', extract: r => r.active !== undefined ? String(r.active) : '' },
  ],
  Practitioner: [
    { key: 'name', label: 'Name', extract: r => {
      const n = r.name?.[0]
      if (!n) return ''
      return [n.family, ...(n.given || [])].filter(Boolean).join(', ')
    }},
    { key: 'gender', label: 'Gender', extract: r => r.gender || '' },
    { key: 'active', label: 'Active', extract: r => r.active !== undefined ? String(r.active) : '' },
  ],
  Location: [
    { key: 'name', label: 'Name', extract: r => r.name || '' },
    { key: 'status', label: 'Status', extract: r => r.status || '' },
    { key: 'type', label: 'Type', extract: r => r.type?.[0]?.coding?.[0]?.display || '' },
  ],
}
/* eslint-enable @typescript-eslint/no-explicit-any */

const FALLBACK_FIELDS = [
  { key: 'lastUpdated', label: 'Last Updated', extract: (r: Record<string, unknown>) => {
    const meta = r.meta as Record<string, unknown> | undefined
    return (meta?.lastUpdated as string) || ''
  }},
]

export function getDisplayFields(resourceType: string) {
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
