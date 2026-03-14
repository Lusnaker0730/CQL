/**
 * TWCORE IG profile URLs and default resource structures.
 * Used by the Visual Bundle Builder to pre-populate new resources
 * with TWCORE-compliant fields.
 */

/** Canonical profile URLs for common TWCORE resource types */
export const TWCORE_PROFILES: Record<string, string> = {
  Patient: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore',
  Encounter: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Encounter-twcore',
  Condition: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Condition-twcore',
  Observation: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-laboratoryResult-twcore',
  MedicationRequest: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/MedicationRequest-twcore',
  Procedure: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Procedure-twcore',
  DiagnosticReport: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/DiagnosticReport-twcore',
  AllergyIntolerance: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/AllergyIntolerance-twcore',
  Organization: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Organization-twcore',
  Practitioner: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Practitioner-twcore',
  Medication: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Medication-twcore',
  ImagingStudy: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/ImagingStudy-twcore',
  Composition: 'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Composition-twcore',
}

/**
 * Pre-populated default structures per resource type.
 * These are merged into new resources created via AddResourceButton.
 * The `id` field is set dynamically and should NOT be included here.
 */
export const TWCORE_RESOURCE_DEFAULTS: Record<string, Record<string, unknown>> = {
  Patient: {
    meta: {
      profile: [TWCORE_PROFILES.Patient],
    },
    identifier: [
      {
        use: 'official',
        type: {
          coding: [
            {
              system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
              code: 'NNxxx',
              display: 'National Person Identifier',
            },
          ],
        },
        system: 'http://www.moi.gov.tw/',
      },
    ],
    name: [{ use: 'official', text: '' }],
    gender: '',
    birthDate: '',
  },
  Encounter: {
    meta: {
      profile: [TWCORE_PROFILES.Encounter],
    },
    status: '',
    class: {},
    subject: {},
  },
  Condition: {
    meta: {
      profile: [TWCORE_PROFILES.Condition],
    },
    clinicalStatus: {
      coding: [
        {
          system: 'http://terminology.hl7.org/CodeSystem/condition-clinical',
          code: 'active',
          display: 'Active',
        },
      ],
    },
    category: [
      {
        coding: [
          {
            system: 'http://terminology.hl7.org/CodeSystem/condition-category',
            code: 'encounter-diagnosis',
            display: 'Encounter Diagnosis',
          },
        ],
      },
    ],
    code: {},
    subject: {},
  },
  Observation: {
    meta: {
      profile: [TWCORE_PROFILES.Observation],
    },
    status: 'final',
    category: [
      {
        coding: [
          {
            system: 'http://terminology.hl7.org/CodeSystem/observation-category',
            code: 'laboratory',
            display: 'Laboratory',
          },
        ],
      },
    ],
    code: {},
    subject: {},
  },
  MedicationRequest: {
    meta: {
      profile: [TWCORE_PROFILES.MedicationRequest],
    },
    status: 'active',
    intent: 'order',
    medicationCodeableConcept: {},
    subject: {},
  },
  Procedure: {
    meta: {
      profile: [TWCORE_PROFILES.Procedure],
    },
    status: 'completed',
    code: {},
    subject: {},
  },
  DiagnosticReport: {
    meta: {
      profile: [TWCORE_PROFILES.DiagnosticReport],
    },
    status: 'final',
    category: [
      {
        coding: [
          {
            system: 'http://terminology.hl7.org/CodeSystem/v2-0074',
            code: 'LAB',
            display: 'Laboratory',
          },
        ],
      },
    ],
    code: {},
    subject: {},
  },
  AllergyIntolerance: {
    meta: {
      profile: [TWCORE_PROFILES.AllergyIntolerance],
    },
    clinicalStatus: {
      coding: [
        {
          system: 'http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical',
          code: 'active',
          display: 'Active',
        },
      ],
    },
    code: {},
    patient: {},
  },
  Organization: {
    meta: {
      profile: [TWCORE_PROFILES.Organization],
    },
    name: '',
    type: [],
  },
  Practitioner: {
    meta: {
      profile: [TWCORE_PROFILES.Practitioner],
    },
    name: [{ use: 'official', text: '' }],
  },
}
