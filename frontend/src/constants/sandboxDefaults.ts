/**
 * Default test data for the CDS Sandbox panel.
 * Extracted from SandboxPanel to keep component code clean
 * and make sample data easy to find and modify.
 */

export const DEFAULT_PATIENT_ID = 'test-patient-1'

export const DEFAULT_PREFETCH = {
  patient: {
    resourceType: 'Patient',
    id: DEFAULT_PATIENT_ID,
    meta: {
      profile: ['https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore'],
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
        value: 'A123456789',
      },
    ],
    name: [{ use: 'official', text: 'Test Patient', given: ['Test'], family: 'Patient' }],
    gender: 'male',
    birthDate: '1980-01-01',
  },
}
