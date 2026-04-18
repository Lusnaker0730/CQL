import { describe, it, expect } from 'vitest'
import {
  generatePatient,
  generateCondition,
  generateObservation,
  generateMedication,
  generateMedicationRequest,
  generateAllergyIntolerance,
} from '../fhirPatientGenerator'
import type {
  ConditionItem,
  ObservationItem,
  MedicationItem,
  AllergyItem,
} from '../../config/twcore/types'

// ── Fixtures ──
const PATIENT_ID = 'pat-test-001'

const DIABETES: ConditionItem = {
  code: 'E11.9',
  display: 'Type 2 diabetes mellitus without complications',
  system: 'http://hl7.org/fhir/sid/icd-10-cm',
}

const A1C: ObservationItem = {
  code: '17856-6',
  display: 'Hemoglobin A1c',
  unit: '%',
  ucum_code: '%',
  min_val: 4.0,
  max_val: 12.0,
}

const METFORMIN: MedicationItem = {
  code: 'A10BA02',
  display: 'Metformin 500mg',
  system: 'http://www.whocc.no/atc',
  form_code: 'TAB',
  form_display: 'Tablet',
}

const PEANUT: AllergyItem = {
  code: '91936005',
  display: 'Allergy to peanut',
  system: 'http://snomed.info/sct',
  category: 'food',
  criticality: 'high',
}

describe('fhirPatientGenerator', () => {
  describe('generatePatient', () => {
    it('produces a TW Core-compliant Patient resource', () => {
      const p = generatePatient()
      expect(p.resourceType).toBe('Patient')
      expect(p.id).toMatch(/^pat-/)
      expect((p.meta as { profile: string[] })?.profile).toContain(
        'https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore'
      )
      const identifiers = p.identifier as Array<{ value: string }>
      expect(identifiers[0].value).toMatch(/^[A-Z][12]\d{8}$/) // NHI ID format
      expect(p.gender).toMatch(/^(male|female)$/)
      expect(p.birthDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      const addr = (p.address as Array<{ country: string }>)[0]
      expect(addr.country).toBe('TW')
    })

    it('each patient has a unique id', () => {
      const ids = new Set<string>()
      for (let i = 0; i < 20; i++) ids.add(generatePatient().id as string)
      expect(ids.size).toBe(20)
    })

    it('telecom includes mobile + email entries', () => {
      const p = generatePatient()
      const telecom = p.telecom as Array<{ system: string; value: string }>
      expect(telecom.some((t) => t.system === 'phone' && t.use === 'mobile')).toBe(true)
      expect(telecom.some((t) => t.system === 'email')).toBe(true)
    })
  })

  describe('generateCondition', () => {
    it('wires subject, code, clinicalStatus and dates correctly', () => {
      const cond = generateCondition(PATIENT_ID, DIABETES, '2026-01-01', '2026-12-31')
      expect(cond.resourceType).toBe('Condition')
      expect((cond.subject as { reference: string }).reference).toBe(`Patient/${PATIENT_ID}`)
      const coding = (cond.code as { coding: Array<{ code: string }> }).coding
      expect(coding[0].code).toBe('E11.9')

      const clinical = cond.clinicalStatus as { coding: Array<{ code: string }> }
      expect(clinical.coding[0].code).toBe('active')

      // onsetDateTime must be within requested range (ignoring time portion)
      const onsetDate = (cond.onsetDateTime as string).slice(0, 10)
      expect(onsetDate >= '2026-01-01').toBe(true)
      expect(onsetDate <= '2026-12-31').toBe(true)
    })
  })

  describe('generateObservation', () => {
    it('produces a final-status vital-signs Observation in range', () => {
      const obs = generateObservation(PATIENT_ID, A1C, '2026-01-01', '2026-12-31')
      expect(obs.resourceType).toBe('Observation')
      expect(obs.status).toBe('final')

      const codingArr = (obs.code as { coding: Array<{ code: string }> }).coding
      expect(codingArr[0].code).toBe('17856-6')

      const vq = obs.valueQuantity as { value: number; unit: string }
      expect(vq.value).toBeGreaterThanOrEqual(4.0)
      expect(vq.value).toBeLessThanOrEqual(12.0)
      expect(vq.unit).toBe('%')

      // effectiveDateTime falls within requested range
      const effDate = (obs.effectiveDateTime as string).slice(0, 10)
      expect(effDate >= '2026-01-01').toBe(true)
      expect(effDate <= '2026-12-31').toBe(true)
    })
  })

  describe('generateMedication', () => {
    it('creates a Medication resource with ATC code', () => {
      const med = generateMedication(METFORMIN)
      expect(med.resourceType).toBe('Medication')
      const code = (med.code as { coding: Array<{ code: string; system: string }> }).coding[0]
      expect(code.code).toBe('A10BA02')
      expect(code.system).toBe('http://www.whocc.no/atc')
    })
  })

  describe('generateMedicationRequest', () => {
    it('links to patient, medication, and has authoredOn in range', () => {
      const req = generateMedicationRequest(PATIENT_ID, METFORMIN, '2026-01-01', '2026-12-31')
      expect(req.resourceType).toBe('MedicationRequest')
      expect((req.subject as { reference: string }).reference).toBe(`Patient/${PATIENT_ID}`)
      const authored = (req.authoredOn as string).slice(0, 10)
      expect(authored >= '2026-01-01').toBe(true)
      expect(authored <= '2026-12-31').toBe(true)
    })
  })

  describe('generateAllergyIntolerance', () => {
    it('creates AllergyIntolerance with correct patient ref + criticality', () => {
      const allergy = generateAllergyIntolerance(PATIENT_ID, PEANUT)
      expect(allergy.resourceType).toBe('AllergyIntolerance')
      expect((allergy.patient as { reference: string }).reference).toBe(`Patient/${PATIENT_ID}`)
      expect(allergy.criticality).toBe('high')
    })
  })
})
