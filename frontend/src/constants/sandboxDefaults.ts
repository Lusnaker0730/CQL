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
    name: [{ given: ['Test'], family: 'Patient' }],
    gender: 'male',
    birthDate: '1980-01-01',
  },
  observations: {
    resourceType: 'Bundle',
    type: 'searchset',
    entry: [
      {
        resource: {
          resourceType: 'Observation',
          id: 'obs-1',
          status: 'final',
          category: [
            {
              coding: [
                {
                  system: 'http://terminology.hl7.org/CodeSystem/observation-category',
                  code: 'vital-signs',
                },
              ],
            },
          ],
          code: {
            coding: [{ system: 'http://loinc.org', code: '29463-7', display: 'Body Weight' }],
          },
          subject: { reference: `Patient/${DEFAULT_PATIENT_ID}` },
          valueQuantity: { value: 85, unit: 'kg' },
        },
      },
    ],
  },
}
