/**
 * Converts between FHIR Bundle (collection) and CDS Hooks prefetch format.
 *
 * Bundle format (used by Visual Builder):
 *   { resourceType: "Bundle", type: "collection", entry: [{ resource: {...} }] }
 *
 * Prefetch format (used by CDS Sandbox API):
 *   { patient: { resourceType: "Patient", ... }, observations: { resourceType: "Bundle", type: "searchset", entry: [...] } }
 */

const PLURAL_MAP: Record<string, string> = {
  Patient: 'patient',
  Observation: 'observations',
  Condition: 'conditions',
  Procedure: 'procedures',
  Encounter: 'encounters',
  MedicationRequest: 'medicationRequests',
  MedicationStatement: 'medicationStatements',
  AllergyIntolerance: 'allergyIntolerances',
  Immunization: 'immunizations',
  DiagnosticReport: 'diagnosticReports',
  CarePlan: 'carePlans',
  Goal: 'goals',
  ServiceRequest: 'serviceRequests',
  Coverage: 'coverages',
  Claim: 'claims',
}

function pluralKey(resourceType: string): string {
  return PLURAL_MAP[resourceType] || resourceType.charAt(0).toLowerCase() + resourceType.slice(1) + 's'
}

/** Find resourceType from a plural key (reverse lookup) */
function resourceTypeFromKey(key: string): string | null {
  for (const [rt, pk] of Object.entries(PLURAL_MAP)) {
    if (pk === key) return rt
  }
  return null
}

/**
 * Convert a collection Bundle JSON string to a prefetch object.
 * Patient resources are placed directly; other resources are grouped into searchset Bundles.
 */
export function bundleToPrefetch(bundleJson: string): Record<string, unknown> {
  const parsed = JSON.parse(bundleJson)
  const entries: Array<{ resource: Record<string, unknown> }> = parsed?.entry || []

  const result: Record<string, unknown> = {}
  const grouped: Record<string, Array<{ resource: Record<string, unknown> }>> = {}

  for (const entry of entries) {
    const resource = entry.resource
    if (!resource || !resource.resourceType) continue

    const rt = resource.resourceType as string
    if (rt === 'Patient') {
      result.patient = resource
    } else {
      const key = pluralKey(rt)
      if (!grouped[key]) grouped[key] = []
      grouped[key].push({ resource })
    }
  }

  for (const [key, groupEntries] of Object.entries(grouped)) {
    result[key] = {
      resourceType: 'Bundle',
      type: 'searchset',
      entry: groupEntries,
    }
  }

  return result
}

/**
 * Convert a prefetch object back to a collection Bundle JSON string.
 */
export function prefetchToBundle(prefetch: Record<string, unknown>): string {
  const entries: Array<{ resource: Record<string, unknown> }> = []

  for (const [key, value] of Object.entries(prefetch)) {
    if (!value || typeof value !== 'object') continue
    const val = value as Record<string, unknown>

    if (key === 'patient' || val.resourceType !== 'Bundle') {
      // Direct resource (e.g. patient)
      if (val.resourceType) {
        entries.push({ resource: val })
      }
    } else {
      // Searchset bundle — extract entries
      const bundleEntries = val.entry as Array<{ resource: Record<string, unknown> }> | undefined
      if (Array.isArray(bundleEntries)) {
        for (const be of bundleEntries) {
          if (be.resource) {
            // If resource doesn't have resourceType, try to infer from key
            if (!be.resource.resourceType) {
              const rt = resourceTypeFromKey(key)
              if (rt) be.resource.resourceType = rt
            }
            entries.push({ resource: be.resource })
          }
        }
      }
    }
  }

  const bundle = {
    resourceType: 'Bundle',
    type: 'collection',
    entry: entries,
  }

  return JSON.stringify(bundle, null, 2)
}
