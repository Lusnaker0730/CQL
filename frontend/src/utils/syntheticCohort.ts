/**
 * PAT-255 — a reproducible synthetic cohort for the seeded demo measure `DiabetesHbA1cRate`,
 * with an independent oracle.
 *
 * The TW Core patient generator (the one behind the 病人資料產生器 page) is driven with a fixed
 * seed and a pinned reference date, so `buildSyntheticCohort(DEFAULT_COHORT)` yields the same
 * bundle bit for bit. The oracle re-implements the measure's population logic in plain TypeScript
 * over the generated resources — no CQL, no engine — so the smoke harness can assert the exact
 * initial population / denominator / numerator the platform must report for scenario 43.
 *
 * Generated dates are kept strictly inside the measurement period (a month in from each end), so
 * the oracle never has to reproduce CQL's boundary / precision semantics: an encounter either is
 * in the window or does not exist. What varies per patient is what the measure keys on — whether
 * any encounter is ambulatory / inpatient (emergency-only patients are out of the initial
 * population), whether a diabetes diagnosis AND an A10 prescription are both present
 * (denominator), and whether an HbA1c or glycated-albumin result exists (numerator).
 */
import { setSeed, setReferenceDate, random, randomInt } from './random'
import { generateCompletePatient, generateCustomPatient, resetIdSequence, toTransactionBundle } from './fhirPatientGenerator'
import type { TransactionBundle } from './fhirPatientGenerator'
import { scenariosConfig } from '../config/twcore'
import type { GeneratedPatientData } from '../config/twcore'

export interface CohortPeriod {
  start: string
  end: string
}

export interface CohortOptions {
  seed: number
  patients: number
  /** Share of patients drawn from the diabetes scenario template (the rest are random batch patients). */
  diabetesShare: number
  period: CohortPeriod
  /** Pinned "now" for ages and default windows. */
  referenceDate: string
  /** Generated clinical dates fall in this window; keep it strictly inside `period`. */
  dateFrom: string
  dateTo: string
}

export interface CohortOracle {
  initialPopulation: number
  denominator: number
  numerator: number
  /** Percentage with three decimals (what `$evaluate-measure` reports), null when the denominator is empty. */
  score: number | null
}

export interface CohortStats {
  patients: number
  resources: number
  diabetesTemplatePatients: number
  patientsWithoutQualifyingEncounter: number
}

export interface SyntheticCohort {
  options: CohortOptions
  patients: GeneratedPatientData[]
  bundle: TransactionBundle
  oracle: CohortOracle
  stats: CohortStats
}

export const DEFAULT_COHORT: CohortOptions = {
  seed: 20261009,
  patients: 120,
  diabetesShare: 0.4,
  period: { start: '2025-01-01', end: '2025-12-31' },
  referenceDate: '2025-12-15',
  dateFrom: '2025-02-01',
  dateTo: '2025-11-30',
}

const ICD10CM = 'http://hl7.org/fhir/sid/icd-10-cm'
const ATC = 'http://www.whocc.no/atc'
const LOINC = 'http://loinc.org'
const ACT_CODE = 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

type Coding = { system?: string; code?: string }
type Resource = Record<string, unknown>

function codings(concept: unknown): Coding[] {
  const c = concept as { coding?: Coding[] } | undefined
  return Array.isArray(c?.coding) ? c.coding : []
}

function day(value: unknown): string | null {
  return typeof value === 'string' && value.length >= 10 ? value.slice(0, 10) : null
}

function inPeriod(value: unknown, period: CohortPeriod): boolean {
  const d = day(value)
  return d !== null && d >= period.start && d <= period.end
}

/** `E.status = 'finished' and (E.class ~ AMB or E.class ~ IMP) and E.period overlaps MP` */
function isOutpatientEncounter(e: Resource, period: CohortPeriod): boolean {
  if (e.status !== 'finished') return false
  const cls = e.class as Coding | undefined
  if (!cls || cls.system !== ACT_CODE || (cls.code !== 'AMB' && cls.code !== 'IMP')) return false
  const p = e.period as { start?: string; end?: string } | undefined
  const start = day(p?.start)
  const end = day(p?.end) ?? start
  return start !== null && end !== null && start <= period.end && end >= period.start
}

/** ICD-10-CM E08–E13 */
function isDiabetesCondition(c: Resource): boolean {
  return codings(c.code).some((k) => k.system === ICD10CM && /^E(08|09|1[0-3])/.test(k.code ?? ''))
}

/** `MR.status in {active, completed} and MR.authoredOn during MP and ATC starts with A10` */
function isDiabetesMedication(mr: Resource, period: CohortPeriod): boolean {
  if (mr.status !== 'active' && mr.status !== 'completed') return false
  if (!inPeriod(mr.authoredOn, period)) return false
  return codings(mr.medicationCodeableConcept).some((k) => k.system === ATC && (k.code ?? '').startsWith('A10'))
}

function isFinalInPeriod(o: Resource, period: CohortPeriod): boolean {
  return (o.status === 'final' || o.status === 'amended' || o.status === 'corrected') && inPeriod(o.effectiveDateTime, period)
}

/** LOINC 4548-4 or an NHI order code starting with 09006 */
function isHbA1cResult(o: Resource, period: CohortPeriod): boolean {
  return isFinalInPeriod(o, period)
    && codings(o.code).some((k) => (k.system === LOINC && k.code === '4548-4') || (k.code ?? '').startsWith('09006'))
}

/** LOINC 13980-8 or an NHI order code starting with 09139 */
function isGlycatedAlbuminResult(o: Resource, period: CohortPeriod): boolean {
  return isFinalInPeriod(o, period)
    && codings(o.code).some((k) => (k.system === LOINC && k.code === '13980-8') || (k.code ?? '').startsWith('09139'))
}

export interface PatientVerdict {
  initialPopulation: boolean
  denominator: boolean
  numerator: boolean
}

/** The demo measure's population logic for one patient, mirrored from its CQL. */
export function judgeDiabetesHbA1c(data: GeneratedPatientData, period: CohortPeriod): PatientVerdict {
  const enc = data.encounters as Resource[]
  const cond = data.conditions as Resource[]
  const meds = data.medication_requests as Resource[]
  const obs = data.observations as Resource[]
  const initialPopulation = enc.some((e) => isOutpatientEncounter(e, period))
  const denominator = initialPopulation && cond.some(isDiabetesCondition) && meds.some((m) => isDiabetesMedication(m, period))
  const numerator = denominator && obs.some((o) => isHbA1cResult(o, period) || isGlycatedAlbuminResult(o, period))
  return { initialPopulation, denominator, numerator }
}

export function evaluateDiabetesHbA1c(patients: GeneratedPatientData[], period: CohortPeriod): CohortOracle {
  let initialPopulation = 0
  let denominator = 0
  let numerator = 0
  for (const p of patients) {
    const v = judgeDiabetesHbA1c(p, period)
    if (v.initialPopulation) initialPopulation++
    if (v.denominator) denominator++
    if (v.numerator) numerator++
  }
  const score = denominator > 0 ? Math.round((numerator / denominator) * 100 * 1000) / 1000 : null
  return { initialPopulation, denominator, numerator, score }
}

/** 0–3 encounters; a few patients have none at all, so the initial population is not everyone. */
function encounterCount(): number {
  const r = random()
  if (r < 0.12) return 0
  if (r < 0.5) return 1
  if (r < 0.85) return 2
  return 3
}

function diabetesPatient(opts: CohortOptions): GeneratedPatientData {
  const template = scenariosConfig.scenarios.find((s) => s.id === 'diabetes')
  if (!template) throw new Error('diabetes scenario template missing from config/twcore/scenarios.json')
  // which labs this diabetic had: HbA1c ~65 % of the time, the rest of the template's labs at random
  const labs = template.observations.filter((code) => (code === '4548-4' ? random() < 0.65 : random() < 0.6))
  // ~80 % are on at least one antidiabetic; the others are diagnosed but untreated (not in the denominator)
  const meds = random() < 0.8 ? template.medications.filter(() => random() < 0.6) : []
  if (random() < 0.8 && meds.length === 0 && template.medications.length > 0) meds.push(template.medications[0])
  return generateCustomPatient({
    selectedConditions: [random() < 0.85 ? 'E11.9' : 'E10.9'],
    selectedObservations: labs,
    selectedMedications: meds,
    selectedAllergies: [],
    numEncounters: encounterCount(),
    dateFrom: opts.dateFrom,
    dateTo: opts.dateTo,
  })
}

function randomPatient(opts: CohortOptions): GeneratedPatientData {
  return generateCompletePatient({
    numPatients: 1,
    numEncounters: encounterCount(),
    numConditions: randomInt(1, 3),
    numObservations: randomInt(2, 5),
    numMedications: randomInt(0, 2),
    numAllergies: randomInt(0, 1),
    dateFrom: opts.dateFrom,
    dateTo: opts.dateTo,
  })
}

export function buildSyntheticCohort(options: CohortOptions = DEFAULT_COHORT): SyntheticCohort {
  setSeed(options.seed)
  setReferenceDate(options.referenceDate)
  resetIdSequence(options.seed)
  try {
    const patients: GeneratedPatientData[] = []
    let diabetesTemplatePatients = 0
    for (let i = 0; i < options.patients; i++) {
      if (random() < options.diabetesShare) {
        patients.push(diabetesPatient(options))
        diabetesTemplatePatients++
      } else {
        patients.push(randomPatient(options))
      }
    }
    const bundle = toTransactionBundle(patients)
    const oracle = evaluateDiabetesHbA1c(patients, options.period)
    const stats: CohortStats = {
      patients: patients.length,
      resources: bundle.entry.length,
      diabetesTemplatePatients,
      patientsWithoutQualifyingEncounter: patients.length - oracle.initialPopulation,
    }
    return { options, patients, bundle, oracle, stats }
  } finally {
    setSeed(null)
    setReferenceDate(null)
    resetIdSequence()
  }
}
