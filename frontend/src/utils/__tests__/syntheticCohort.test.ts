import { describe, it, expect } from 'vitest'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildSyntheticCohort, DEFAULT_COHORT, judgeDiabetesHbA1c, evaluateDiabetesHbA1c } from '../syntheticCohort'
import type { GeneratedPatientData } from '../../config/twcore'

/**
 * PAT-255 — the synthetic cohort behind smoke scenario 43 and its oracle.
 *
 * `npm run gen:cohort` (WRITE_SMOKE_FIXTURE=1) rewrites scripts/smoke/scenarios/43-synthetic-cohort/
 * bundle.json + expected.json from the same seed; the last test here fails when the generator (or
 * its clinical config) drifted away from the committed fixture, so the regeneration is a conscious
 * step and the oracle in expected.json never goes stale.
 */
const SCENARIO_DIR = resolve(__dirname, '../../../../scripts/smoke/scenarios/43-synthetic-cohort')
const PERIOD = DEFAULT_COHORT.period

const sha256 = (text: string) => createHash('sha256').update(text).digest('hex')

function patient(parts: Partial<GeneratedPatientData>): GeneratedPatientData {
  return {
    patient: { resourceType: 'Patient', id: 'p' },
    encounters: [],
    conditions: [],
    observations: [],
    medications: [],
    medication_requests: [],
    allergies: [],
    ...parts,
  }
}
const encounter = (cls: string, date = '2025-05-05', status = 'finished') => ({
  resourceType: 'Encounter', id: 'e', status,
  class: { system: 'http://terminology.hl7.org/CodeSystem/v3-ActCode', code: cls },
  period: { start: `${date}T09:00:00+08:00`, end: `${date}T10:00:00+08:00` },
})
const diabetes = (code = 'E11.9') => ({
  resourceType: 'Condition', id: 'c', code: { coding: [{ system: 'http://hl7.org/fhir/sid/icd-10-cm', code }] },
})
const metformin = (date = '2025-06-01', status = 'active') => ({
  resourceType: 'MedicationRequest', id: 'm', status, authoredOn: `${date}T10:00:00+08:00`,
  medicationCodeableConcept: { coding: [{ system: 'http://www.nlm.nih.gov/research/umls/rxnorm', code: '6809' }, { system: 'http://www.whocc.no/atc', code: 'A10BA02' }] },
})
const lab = (code: string, date = '2025-07-07', status = 'final', system = 'http://loinc.org') => ({
  resourceType: 'Observation', id: 'o', status, effectiveDateTime: `${date}T08:30:00+08:00`,
  code: { coding: [{ system, code }] },
})

describe('judgeDiabetesHbA1c — the demo measure, mirrored', () => {
  it('counts an HbA1c-tested, treated diabetic with an ambulatory visit in every population', () => {
    const v = judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], conditions: [diabetes()], medication_requests: [metformin()], observations: [lab('4548-4')] }), PERIOD)
    expect(v).toEqual({ initialPopulation: true, denominator: true, numerator: true })
  })

  it('an emergency-only patient is outside the initial population; an unfinished visit does not count', () => {
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('EMER')], conditions: [diabetes()], medication_requests: [metformin()] }), PERIOD).initialPopulation).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB', '2025-05-05', 'in-progress')] }), PERIOD).initialPopulation).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('IMP')] }), PERIOD).initialPopulation).toBe(true)
  })

  it('the denominator needs both the diagnosis (E08–E13) and an A10 prescription inside the period', () => {
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], conditions: [diabetes()] }), PERIOD).denominator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], medication_requests: [metformin()] }), PERIOD).denominator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], conditions: [diabetes('I10')], medication_requests: [metformin()] }), PERIOD).denominator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], conditions: [diabetes()], medication_requests: [metformin('2024-06-01')] }), PERIOD).denominator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], conditions: [diabetes('E10.9')], medication_requests: [metformin('2025-06-01', 'completed')] }), PERIOD).denominator).toBe(true)
  })

  it('the numerator is a denominator member with an HbA1c or glycated-albumin result in the period', () => {
    const base = { encounters: [encounter('AMB')], conditions: [diabetes()], medication_requests: [metformin()] }
    expect(judgeDiabetesHbA1c(patient({ ...base, observations: [lab('4548-4', '2024-12-20')] }), PERIOD).numerator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ ...base, observations: [lab('4548-4', '2025-07-07', 'preliminary')] }), PERIOD).numerator).toBe(false)
    expect(judgeDiabetesHbA1c(patient({ ...base, observations: [lab('17856-6')] }), PERIOD).numerator).toBe(false) // not the code the measure names
    expect(judgeDiabetesHbA1c(patient({ ...base, observations: [lab('13980-8')] }), PERIOD).numerator).toBe(true)
    expect(judgeDiabetesHbA1c(patient({ ...base, observations: [lab('09006C', '2025-03-03', 'final', 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw')] }), PERIOD).numerator).toBe(true)
    // an HbA1c without the diagnosis is not a numerator hit: the numerator is a subset of the denominator
    expect(judgeDiabetesHbA1c(patient({ encounters: [encounter('AMB')], observations: [lab('4548-4')] }), PERIOD).numerator).toBe(false)
  })
})

describe('buildSyntheticCohort', () => {
  it('is deterministic for a seed and sensitive to it', () => {
    const a = buildSyntheticCohort(DEFAULT_COHORT)
    const b = buildSyntheticCohort(DEFAULT_COHORT)
    expect(JSON.stringify(b.bundle)).toBe(JSON.stringify(a.bundle))
    expect(b.oracle).toEqual(a.oracle)
    const c = buildSyntheticCohort({ ...DEFAULT_COHORT, seed: DEFAULT_COHORT.seed + 1 })
    expect(JSON.stringify(c.bundle)).not.toBe(JSON.stringify(a.bundle))
  })

  it('produces a cohort the measure can actually discriminate on', () => {
    const { oracle, stats, patients } = buildSyntheticCohort(DEFAULT_COHORT)
    expect(patients).toHaveLength(DEFAULT_COHORT.patients)
    expect(oracle.numerator).toBeLessThanOrEqual(oracle.denominator)
    expect(oracle.denominator).toBeLessThanOrEqual(oracle.initialPopulation)
    expect(oracle.initialPopulation).toBeLessThan(patients.length) // some have no qualifying encounter
    expect(oracle.denominator).toBeGreaterThanOrEqual(15)
    expect(oracle.numerator).toBeGreaterThanOrEqual(5)
    expect(oracle.numerator).toBeLessThan(oracle.denominator) // some treated diabetics skipped the test
    expect(oracle.score).not.toBeNull()
    expect(stats.resources).toBeGreaterThan(patients.length * 3)
    // every generated date sits inside the measurement period, so no CQL boundary semantics are in play
    const dates = JSON.stringify(patients).match(/20\d\d-\d\d-\d\dT/g) ?? []
    expect(dates.length).toBeGreaterThan(0)
    expect(dates.every((d) => d >= `${DEFAULT_COHORT.dateFrom}T` && d <= `${DEFAULT_COHORT.dateTo}T~`)).toBe(true)
    // the oracle is the sum of the per-patient verdicts (no double counting)
    expect(evaluateDiabetesHbA1c(patients, DEFAULT_COHORT.period)).toEqual(oracle)
  })

  it('matches the committed smoke fixture (run `npm run gen:cohort` after changing the generator or its config)', () => {
    const cohort = buildSyntheticCohort(DEFAULT_COHORT)
    const bundleJson = JSON.stringify(cohort.bundle)
    const expected = {
      type: 'synthetic-cohort',
      measureName: 'DiabetesHbA1cRate',
      seed: DEFAULT_COHORT.seed,
      patientCount: DEFAULT_COHORT.patients,
      period: DEFAULT_COHORT.period,
      populations: {
        'initial-population': cohort.oracle.initialPopulation,
        denominator: cohort.oracle.denominator,
        numerator: cohort.oracle.numerator,
      },
      score: cohort.oracle.score,
      scoreTolerance: 0.01,
      maxEvaluationTimeMs: 30000,
      fixtureSha256: sha256(bundleJson),
      stats: cohort.stats,
      _note: 'PAT-255 synthetic cohort: generated by frontend/src/utils/syntheticCohort.ts from the TW Core patient generator with a fixed seed; populations / score are the oracle computed in TypeScript over the generated resources (frontend/src/utils/__tests__/syntheticCohort.test.ts locks fixtureSha256 to the generator). Regenerate with `npm run gen:cohort` in frontend/.',
    }
    if (process.env.WRITE_SMOKE_FIXTURE === '1') {
      mkdirSync(SCENARIO_DIR, { recursive: true })
      writeFileSync(resolve(SCENARIO_DIR, 'bundle.json'), bundleJson + '\n')
      writeFileSync(resolve(SCENARIO_DIR, 'expected.json'), JSON.stringify(expected, null, 2) + '\n')
      return
    }
    const path = resolve(SCENARIO_DIR, 'expected.json')
    expect(existsSync(path), `missing ${path} — run npm run gen:cohort`).toBe(true)
    const committed = JSON.parse(readFileSync(path, 'utf8'))
    expect(committed.fixtureSha256, 'generator output drifted from the committed fixture — run npm run gen:cohort and commit the result').toBe(expected.fixtureSha256)
    expect(committed.populations).toEqual(expected.populations)
    expect(committed.score).toBe(expected.score)
  })
})
