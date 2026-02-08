export interface FhirServerPreset {
  label: string
  url: string
  description: string
}

export const FHIR_SERVER_PRESETS: FhirServerPreset[] = [
  {
    label: 'HAPI FHIR R4',
    url: 'http://hapi.fhir.org/baseR4',
    description: 'Public HAPI FHIR R4 test server',
  },
  {
    label: 'TW Core FHIR Server',
    url: 'https://twcore.hapi.fhir.tw/fhir',
    description: 'Taiwan Core Implementation Guide (TWCDI) FHIR server',
  },
  {
    label: 'SMART Health IT Sandbox',
    url: 'https://r4.smarthealthit.org',
    description: 'SMART on FHIR R4 sandbox server',
  },
]
