// Taiwan Core Data for Interoperability (TWCDI) - 20 Clinical Data Classes
// Mapped to TW Core FHIR R4 Profiles

import data from './twcdiData.json'

export interface TwcdiDataClass {
  name: string
  fhirResource: string
  profileUrl: string
  description: string
}

export const TWCDI_DATA_CLASSES: TwcdiDataClass[] = data.dataClasses

// Taiwan-relevant code system URIs
export const TW_CODE_SYSTEMS = data.codeSystems as {
  readonly LOINC: string
  readonly SNOMED_CT: string
  readonly ICD_10_CM_TW: string
  readonly ICD_10_PCS_TW: string
  readonly NHI_MEDICATION: string
  readonly NHI_PROCEDURE: string
  readonly TWMedicalDepartment: string
}

// Common LOINC codes used in TW Core vital signs & labs
export const TW_COMMON_LOINC_CODES = data.commonLoincCodes as {
  readonly [key: string]: { readonly code: string; readonly display: string }
}
