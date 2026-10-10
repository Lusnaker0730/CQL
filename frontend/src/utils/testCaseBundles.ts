/**
 * PAT-247 — recognising a MADiE-style test case bundle on the client, so the import dialog can hand
 * it to the server (which turns the bundle's MeasureReport into the structured expectation) instead
 * of parsing it as a bare patient bundle.
 */

/** The CQF Measures IG profile MADiE (and our export) put on the MeasureReport that carries the expectation. */
export const TEST_CASE_REPORT_PROFILE = 'http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm'

interface BundleLike {
  resourceType?: string
  entry?: Array<{
    resource?: {
      resourceType?: string
      meta?: { profile?: string[] }
      modifierExtension?: Array<{ url?: string; valueBoolean?: boolean }>
    }
  }>
}

/** A FHIR Bundle holding a test-case MeasureReport (ours or MADiE's): the server reads the expectation out of it. */
export function isTestCaseBundle(raw: unknown): boolean {
  const bundle = raw as BundleLike | null | undefined
  if (!bundle || bundle.resourceType !== 'Bundle' || !Array.isArray(bundle.entry)) return false
  return bundle.entry.some((e) => {
    const resource = e?.resource
    if (resource?.resourceType !== 'MeasureReport') return false
    return (resource.meta?.profile ?? []).includes(TEST_CASE_REPORT_PROFILE)
      || (resource.modifierExtension ?? []).some((x) => x.url?.endsWith('/cqfm-isTestCase') && x.valueBoolean === true)
  })
}

export const isZipFileName = (name: string) => /\.zip$/i.test(name)
