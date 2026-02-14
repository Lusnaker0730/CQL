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

/** Look up a code system entry by its URL. */
export function findCodeSystemByUrl(url: string): CodeSystemEntry | undefined {
  return ALL_CODE_SYSTEMS.find((cs) => cs.url === url)
}

/** Look up a code system entry by its label. */
export function findCodeSystemByLabel(label: string): CodeSystemEntry | undefined {
  return ALL_CODE_SYSTEMS.find((cs) => cs.label === label)
}
