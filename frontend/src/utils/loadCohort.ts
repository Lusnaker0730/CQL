/**
 * PAT-266 — the synthetic cohort behind the k6 load test (scripts/load).
 *
 * Built by the same TW Core patient generator and judged by the same TypeScript oracle as the smoke
 * cohort (PAT-255, utils/syntheticCohort.ts), just bigger: 10 000 patients by default, cut into
 * transaction bundles of `chunkPatients` so HAPI ingests them one request at a time, with the demo
 * measure's expected populations and score computed for every checkpoint prefix (500, 1 000, 2 000,
 * 5 000, 10 000 patients). The load test seeds chunks up to a checkpoint, evaluates the measure and
 * compares the answer with that checkpoint's oracle — so scale is tested for correctness, not only
 * for speed.
 *
 * `npm run gen:load-cohort [-- <size>]` writes scripts/load/data/cohort-<size>/ (gitignored; tens of
 * megabytes) from the fixed seed; see src/utils/__tests__/loadCohort.test.ts.
 */
import { buildSyntheticCohort, DEFAULT_COHORT, evaluateDiabetesHbA1c } from './syntheticCohort'
import type { CohortOptions, CohortOracle } from './syntheticCohort'
import { toTransactionBundle } from './fhirPatientGenerator'
import type { TransactionBundle } from './fhirPatientGenerator'
import type { GeneratedPatientData } from '../config/twcore'

export interface LoadCohortOptions extends CohortOptions {
  /** Patients per transaction bundle (one HAPI transaction each). */
  chunkPatients: number
  /** Cohort sizes the load test evaluates at, ascending; each a multiple of `chunkPatients` and at most `patients`. */
  checkpoints: number[]
}

export interface LoadCheckpoint {
  patients: number
  /** How many chunks (from the first) make up this checkpoint. */
  chunks: number
  /** Resources in those chunks. */
  resources: number
  oracle: CohortOracle
}

export interface LoadCohort {
  options: LoadCohortOptions
  patients: GeneratedPatientData[]
  chunks: TransactionBundle[]
  checkpoints: LoadCheckpoint[]
  /** A few patient ids from the first chunk, for single-patient CQL executions under load. */
  samplePatientIds: string[]
  stats: { patients: number; resources: number }
}

export const DEFAULT_LOAD_COHORT: LoadCohortOptions = {
  ...DEFAULT_COHORT,
  seed: 20261015,
  patients: 10_000,
  chunkPatients: 100,
  checkpoints: [500, 1_000, 2_000, 5_000, 10_000],
}

/** The default options for a cohort of `size` patients: the default checkpoints that fit, ending at `size`. */
export function loadCohortOptions(size: number, base: LoadCohortOptions = DEFAULT_LOAD_COHORT): LoadCohortOptions {
  if (!Number.isInteger(size) || size <= 0 || size % base.chunkPatients !== 0) {
    throw new Error(`cohort size must be a positive multiple of ${base.chunkPatients}, got ${size}`)
  }
  const checkpoints = base.checkpoints.filter((c) => c < size)
  checkpoints.push(size)
  return { ...base, patients: size, checkpoints }
}

export function buildLoadCohort(options: LoadCohortOptions = DEFAULT_LOAD_COHORT): LoadCohort {
  const { chunkPatients, checkpoints, patients: size } = options
  if (!Number.isInteger(chunkPatients) || chunkPatients <= 0) throw new Error(`chunkPatients must be positive, got ${chunkPatients}`)
  if (size % chunkPatients !== 0) throw new Error(`patients (${size}) must be a multiple of chunkPatients (${chunkPatients})`)
  if (checkpoints.length === 0) throw new Error('at least one checkpoint is required')
  checkpoints.forEach((c, i) => {
    if (c % chunkPatients !== 0) throw new Error(`checkpoint ${c} is not a multiple of chunkPatients (${chunkPatients})`)
    if (c > size) throw new Error(`checkpoint ${c} exceeds the cohort size (${size})`)
    if (i > 0 && c <= checkpoints[i - 1]) throw new Error(`checkpoints must be ascending, got ${checkpoints.join(', ')}`)
  })

  const cohort = buildSyntheticCohort(options)
  const chunks: TransactionBundle[] = []
  for (let i = 0; i < cohort.patients.length; i += chunkPatients) {
    chunks.push(toTransactionBundle(cohort.patients.slice(i, i + chunkPatients)))
  }
  const cumulativeResources: number[] = []
  chunks.forEach((chunk, i) => cumulativeResources.push((i === 0 ? 0 : cumulativeResources[i - 1]) + chunk.entry.length))

  const loadCheckpoints: LoadCheckpoint[] = checkpoints.map((n) => ({
    patients: n,
    chunks: n / chunkPatients,
    resources: cumulativeResources[n / chunkPatients - 1],
    oracle: evaluateDiabetesHbA1c(cohort.patients.slice(0, n), options.period),
  }))
  const samplePatientIds = cohort.patients.slice(0, Math.min(50, chunkPatients)).map((p) => p.patient.id as string)

  return {
    options,
    patients: cohort.patients,
    chunks,
    checkpoints: loadCheckpoints,
    samplePatientIds,
    stats: { patients: cohort.patients.length, resources: cohort.bundle.entry.length },
  }
}
