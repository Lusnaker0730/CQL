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
}
