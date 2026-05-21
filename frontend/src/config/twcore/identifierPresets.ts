/**
 * TW Core IG identifier presets for the Visual Bundle Builder.
 * Each preset prefills `Identifier.use`, `Identifier.system`, and
 * `Identifier.type.coding[0]` so the user only has to type the value.
 *
 * Codes follow http://terminology.hl7.org/CodeSystem/v2-0203.
 */
export interface TwIdentifierPreset {
  /** i18n key under measures.testCaseBuilder.identifierPresets */
  labelKey: string
  use: string
  system: string
  typeCode: string
  typeDisplay: string
  /** Optional i18n key for the value-field helperText */
  hintKey?: string
}

export const TW_IDENTIFIER_TYPE_SYSTEM =
  'http://terminology.hl7.org/CodeSystem/v2-0203'

export const TW_IDENTIFIER_PRESETS: readonly TwIdentifierPreset[] = [
  {
    labelKey: 'testCaseBuilder.identifierPresets.nationalId',
    use: 'official',
    system: 'http://www.moi.gov.tw/',
    typeCode: 'NNxxx',
    typeDisplay: 'National Person Identifier',
    hintKey: 'testCaseBuilder.identifierPresets.nationalIdHint',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.passport',
    use: 'official',
    system: 'http://www.boca.gov.tw/',
    typeCode: 'PPN',
    typeDisplay: 'Passport Number',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.residentCert',
    use: 'official',
    system: 'http://www.immigration.gov.tw/',
    typeCode: 'PRC',
    typeDisplay: 'Permanent Resident Card Number',
  },
  {
    labelKey: 'testCaseBuilder.identifierPresets.medicalRecord',
    use: 'usual',
    system: '',
    typeCode: 'MR',
    typeDisplay: 'Medical Record Number',
  },
]
