/**
 * Shared code system definitions used across the CQL Builder and CDS Authoring tools.
 *
 * Each entry maps a human-readable label to its canonical FHIR system URL.
 */

export interface CodeSystemEntry {
  label: string
  url: string
}

/** A small FHIR administrative code system with predefined codes. */
export interface PredefinedCodeSystem {
  label: string
  labelZh: string
  url: string
  alias: string
  codes: Array<{ code: string; display: string; displayZh: string }>
}

/** A group of related predefined code systems. */
export interface CodeSystemGroup {
  label: string
  labelZh: string
  systems: PredefinedCodeSystem[]
}

export const COMMON_CODE_SYSTEMS: CodeSystemEntry[] = [
  { label: 'LOINC', url: 'http://loinc.org' },
  { label: 'SNOMED CT', url: 'http://snomed.info/sct' },
  { label: 'RxNorm', url: 'http://www.nlm.nih.gov/research/umls/rxnorm' },
  { label: 'ICD-9-CM', url: 'http://hl7.org/fhir/sid/icd-9-cm' },
  { label: 'ICD-10-CM', url: 'http://hl7.org/fhir/sid/icd-10-cm' },
  { label: 'CPT', url: 'http://www.ama-assn.org/go/cpt' },
  { label: 'NCI', url: 'http://ncimeta.nci.nih.gov' },
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

/**
 * Grouped FHIR administrative code systems with predefined codes.
 * These are small, well-known code systems where all codes can be listed inline.
 */
export const CODE_SYSTEM_GROUPS: CodeSystemGroup[] = [
  {
    label: 'AllergyIntolerance',
    labelZh: '過敏與不耐症',
    systems: [
      {
        label: 'Clinical Status',
        labelZh: '臨床狀態',
        url: 'http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical',
        alias: 'AllergyClinicalStatus',
        codes: [
          { code: 'active', display: 'Active', displayZh: '活躍' },
          { code: 'inactive', display: 'Inactive', displayZh: '不活躍' },
          { code: 'resolved', display: 'Resolved', displayZh: '已解決' },
        ],
      },
      {
        label: 'Verification Status',
        labelZh: '驗證狀態',
        url: 'http://terminology.hl7.org/CodeSystem/allergyintolerance-verification',
        alias: 'AllergyVerificationStatus',
        codes: [
          { code: 'unconfirmed', display: 'Unconfirmed', displayZh: '未確認' },
          { code: 'presumed', display: 'Presumed', displayZh: '推定' },
          { code: 'confirmed', display: 'Confirmed', displayZh: '已確認' },
          { code: 'refuted', display: 'Refuted', displayZh: '已排除' },
          { code: 'entered-in-error', display: 'Entered in Error', displayZh: '誤錄' },
        ],
      },
      {
        label: 'Category',
        labelZh: '類別',
        url: 'http://hl7.org/fhir/allergy-intolerance-category',
        alias: 'AllergyIntoleranceCategory',
        codes: [
          { code: 'food', display: 'Food', displayZh: '食物' },
          { code: 'medication', display: 'Medication', displayZh: '藥物' },
          { code: 'environment', display: 'Environment', displayZh: '環境' },
          { code: 'biologic', display: 'Biologic', displayZh: '生物製品' },
        ],
      },
      {
        label: 'Type',
        labelZh: '類型',
        url: 'http://hl7.org/fhir/allergy-intolerance-type',
        alias: 'AllergyIntoleranceType',
        codes: [
          { code: 'allergy', display: 'Allergy', displayZh: '過敏' },
          { code: 'intolerance', display: 'Intolerance', displayZh: '不耐症' },
        ],
      },
      {
        label: 'Criticality',
        labelZh: '嚴重度',
        url: 'http://hl7.org/fhir/allergy-intolerance-criticality',
        alias: 'AllergyIntoleranceCriticality',
        codes: [
          { code: 'low', display: 'Low Risk', displayZh: '低風險' },
          { code: 'high', display: 'High Risk', displayZh: '高風險' },
          { code: 'unable-to-assess', display: 'Unable to Assess', displayZh: '無法評估' },
        ],
      },
    ],
  },
  {
    label: 'Condition',
    labelZh: '病情',
    systems: [
      {
        label: 'Clinical Status',
        labelZh: '臨床狀態',
        url: 'http://terminology.hl7.org/CodeSystem/condition-clinical',
        alias: 'ConditionClinicalStatus',
        codes: [
          { code: 'active', display: 'Active', displayZh: '活躍' },
          { code: 'recurrence', display: 'Recurrence', displayZh: '復發' },
          { code: 'relapse', display: 'Relapse', displayZh: '再發' },
          { code: 'inactive', display: 'Inactive', displayZh: '不活躍' },
          { code: 'remission', display: 'Remission', displayZh: '緩解' },
          { code: 'resolved', display: 'Resolved', displayZh: '已解決' },
        ],
      },
      {
        label: 'Verification Status',
        labelZh: '驗證狀態',
        url: 'http://terminology.hl7.org/CodeSystem/condition-ver-status',
        alias: 'ConditionVerificationStatus',
        codes: [
          { code: 'unconfirmed', display: 'Unconfirmed', displayZh: '未確認' },
          { code: 'provisional', display: 'Provisional', displayZh: '暫定' },
          { code: 'differential', display: 'Differential', displayZh: '鑑別' },
          { code: 'confirmed', display: 'Confirmed', displayZh: '已確認' },
          { code: 'refuted', display: 'Refuted', displayZh: '已排除' },
          { code: 'entered-in-error', display: 'Entered in Error', displayZh: '誤錄' },
        ],
      },
    ],
  },
  {
    label: 'MedicationRequest',
    labelZh: '處方',
    systems: [
      {
        label: 'Status',
        labelZh: '狀態',
        url: 'http://hl7.org/fhir/CodeSystem/medicationrequest-status',
        alias: 'MedicationRequestStatus',
        codes: [
          { code: 'active', display: 'Active', displayZh: '活躍' },
          { code: 'on-hold', display: 'On Hold', displayZh: '暫停' },
          { code: 'cancelled', display: 'Cancelled', displayZh: '已取消' },
          { code: 'completed', display: 'Completed', displayZh: '已完成' },
          { code: 'entered-in-error', display: 'Entered in Error', displayZh: '誤錄' },
          { code: 'stopped', display: 'Stopped', displayZh: '已停止' },
          { code: 'draft', display: 'Draft', displayZh: '草稿' },
          { code: 'unknown', display: 'Unknown', displayZh: '未知' },
        ],
      },
    ],
  },
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
