import { DEFAULT_FHIR_SERVER_URL } from '../config/env'

export interface FhirServerPreset {
  label: string
  url: string
  description: string
}

export const FHIR_SERVER_PRESETS: FhirServerPreset[] = [
  {
    label: '內建 HAPI FHIR 伺服器',
    url: DEFAULT_FHIR_SERVER_URL,
    description: '平台內建的 HAPI FHIR R4 伺服器（由後端代理存取）',
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
