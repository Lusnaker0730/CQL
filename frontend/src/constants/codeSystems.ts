/**
 * Shared code system definitions used across the CQL Builder and CDS Authoring tools.
 *
 * Each entry maps a human-readable label to its canonical FHIR system URL.
 */

export interface CodeSystemEntry {
  label: string
  url: string
}

export const COMMON_CODE_SYSTEMS: CodeSystemEntry[] = [
  { label: 'LOINC', url: 'http://loinc.org' },
  { label: 'SNOMED CT', url: 'http://snomed.info/sct' },
  { label: 'RxNorm', url: 'http://www.nlm.nih.gov/research/umls/rxnorm' },
  { label: 'ICD-9-CM', url: 'http://hl7.org/fhir/sid/icd-9-cm' },
  { label: 'ICD-10-CM', url: 'http://hl7.org/fhir/sid/icd-10-cm' },
  { label: 'CPT', url: 'http://www.ama-assn.org/go/cpt' },
  { label: 'NCI', url: 'http://ncimeta.nci.nih.gov' },
  { label: 'Condition Clinical Status', url: 'http://terminology.hl7.org/CodeSystem/condition-clinical' },
  { label: 'Observation Category', url: 'http://terminology.hl7.org/CodeSystem/observation-category' },
  { label: 'Encounter Type (v3-ActCode)', url: 'http://terminology.hl7.org/CodeSystem/v3-ActCode' },
]

export const TW_CODE_SYSTEMS: CodeSystemEntry[] = [
  { label: 'ICD-10-CM (TW)', url: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw' },
  { label: 'ICD-10-PCS (TW)', url: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-pcs-2023-tw' },
  { label: 'ATC (TW)', url: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medcation-atc-tw' },
  { label: 'NHI Medication (TW)', url: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw' },
  { label: 'NHI Department (TW)', url: 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-treatment-department-nhi-tw' },
]

/** All code systems: common standards followed by Taiwan-specific systems. */
export const ALL_CODE_SYSTEMS: CodeSystemEntry[] = [
  ...COMMON_CODE_SYSTEMS,
  ...TW_CODE_SYSTEMS,
]

export const RESOURCE_SUGGESTED_CODES: Record<string, Array<{ code: string; display: string; displayZh: string; system: string; systemLabel: string }>> = {
  Encounter: [
    { code: 'AMB', display: 'Ambulatory', displayZh: '門診', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
    { code: 'EMER', display: 'Emergency', displayZh: '急診', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
    { code: 'IMP', display: 'Inpatient encounter', displayZh: '住院', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
    { code: 'SS', display: 'Short stay', displayZh: '短期住院', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
    { code: 'HH', display: 'Home health', displayZh: '居家照護', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
    { code: 'VR', display: 'Virtual', displayZh: '遠距醫療', system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', systemLabel: 'Encounter Type (v3-ActCode)' },
  ],
}

/** Look up a code system entry by its URL. */
export function findCodeSystemByUrl(url: string): CodeSystemEntry | undefined {
  return ALL_CODE_SYSTEMS.find((cs) => cs.url === url)
}

/** Look up a code system entry by its label. */
export function findCodeSystemByLabel(label: string): CodeSystemEntry | undefined {
  return ALL_CODE_SYSTEMS.find((cs) => cs.label === label)
}
