export interface FhirServerPreset {
  label: string
  url: string
  description: string
}

export const FHIR_SERVER_PRESETS: FhirServerPreset[] = [
  {
    label: 'Local HAPI FHIR (Docker)',
    url: 'http://hapi-fhir:8080/fhir',
    description: 'Local HAPI FHIR R4 server running in Docker',
  },
  {
    label: 'HAPI FHIR R4',
    url: 'https://hapi.fhir.org/baseR4',
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
