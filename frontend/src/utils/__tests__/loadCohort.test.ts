import { describe, it, expect, vi } from 'vitest'
import { existsSync, mkdirSync, readdirSync, unlinkSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildLoadCohort, loadCohortOptions, DEFAULT_LOAD_COHORT } from '../loadCohort'
import { evaluateDiabetesHbA1c } from '../syntheticCohort'

/**
 * PAT-266 — the load-test cohort (scripts/load) and its fixture writer.
 *
 * `npm run gen:load-cohort [-- <size>]` (WRITE_LOAD_FIXTURE=1) writes scripts/load/data/cohort-<size>/:
 * chunk-NNNN.json transaction bundles plus expected.json with the oracle at every checkpoint. The
 * directory is gitignored (a 10 000-patient cohort is ~100 MB); scripts/load/run.sh generates it on
 * demand. Generating 10 000 patients takes a while, hence the long timeout.
 */
vi.setConfig({ testTimeout: 900_000 })

const DATA_ROOT = resolve(__dirname, '../../../../scripts/load/data')
const SMALL = { ...DEFAULT_LOAD_COHORT, patients: 400, chunkPatients: 100, checkpoints: [100, 200, 400] }

describe('buildLoadCohort', () => {
  it('cuts the cohort into fixed-size transaction chunks with unique ids and one oracle per checkpoint prefix', () => {
    const cohort = buildLoadCohort(SMALL)
    expect(cohort.patients).toHaveLength(400)
    expect(cohort.chunks).toHaveLength(4)
    for (const chunk of cohort.chunks) {
      expect(chunk.resourceType).toBe('Bundle')
      expect(chunk.type).toBe('transaction')
      expect(chunk.entry.filter((e) => e.resource.resourceType === 'Patient')).toHaveLength(100)
      expect(chunk.entry.every((e) => e.request.method === 'PUT' && e.request.url === e.fullUrl)).toBe(true)
    }
    const ids = cohort.chunks.flatMap((c) => c.entry.map((e) => e.fullUrl))
    expect(new Set(ids).size).toBe(ids.length)
    expect(cohort.stats.resources).toBe(ids.length)

    expect(cohort.checkpoints.map((c) => c.patients)).toEqual([100, 200, 400])
    expect(cohort.checkpoints.map((c) => c.chunks)).toEqual([1, 2, 4])
    expect(cohort.checkpoints.map((c) => c.resources)).toEqual([
      cohort.chunks[0].entry.length,
      cohort.chunks[0].entry.length + cohort.chunks[1].entry.length,
      ids.length,
    ])
    for (const cp of cohort.checkpoints) {
      expect(cp.oracle).toEqual(evaluateDiabetesHbA1c(cohort.patients.slice(0, cp.patients), SMALL.period))
      expect(cp.oracle.numerator).toBeLessThanOrEqual(cp.oracle.denominator)
      expect(cp.oracle.denominator).toBeLessThanOrEqual(cp.oracle.initialPopulation)
    }
    // a bigger prefix can only add members
    for (let i = 1; i < cohort.checkpoints.length; i++) {
      expect(cohort.checkpoints[i].oracle.initialPopulation).toBeGreaterThanOrEqual(cohort.checkpoints[i - 1].oracle.initialPopulation)
      expect(cohort.checkpoints[i].oracle.denominator).toBeGreaterThanOrEqual(cohort.checkpoints[i - 1].oracle.denominator)
      expect(cohort.checkpoints[i].oracle.numerator).toBeGreaterThanOrEqual(cohort.checkpoints[i - 1].oracle.numerator)
    }
    expect(cohort.samplePatientIds).toHaveLength(50)
    expect(cohort.samplePatientIds.every((id) => cohort.chunks[0].entry.some((e) => e.fullUrl === `Patient/${id}`))).toBe(true)
  })

  it('is deterministic for a seed', () => {
    const a = buildLoadCohort(SMALL)
    const b = buildLoadCohort(SMALL)
    expect(JSON.stringify(b.chunks[3])).toBe(JSON.stringify(a.chunks[3]))
    expect(b.checkpoints).toEqual(a.checkpoints)
  })

  it('rejects checkpoints and sizes that do not line up with the chunks', () => {
    expect(() => buildLoadCohort({ ...SMALL, checkpoints: [150] })).toThrow(/multiple of chunkPatients/)
    expect(() => buildLoadCohort({ ...SMALL, checkpoints: [500] })).toThrow(/exceeds/)
    expect(() => buildLoadCohort({ ...SMALL, checkpoints: [200, 100] })).toThrow(/ascending/)
    expect(() => buildLoadCohort({ ...SMALL, checkpoints: [] })).toThrow(/at least one/)
    expect(() => buildLoadCohort({ ...SMALL, patients: 250 })).toThrow(/multiple of chunkPatients/)
  })

  it('loadCohortOptions keeps the default checkpoints that fit and ends at the requested size', () => {
    expect(loadCohortOptions(2000).checkpoints).toEqual([500, 1000, 2000])
    expect(loadCohortOptions(10_000).checkpoints).toEqual(DEFAULT_LOAD_COHORT.checkpoints)
    expect(loadCohortOptions(300).checkpoints).toEqual([300])
    expect(loadCohortOptions(1500).checkpoints).toEqual([500, 1000, 1500])
    expect(() => loadCohortOptions(250)).toThrow(/multiple of 100/)
    expect(() => loadCohortOptions(0)).toThrow()
  })
})

describe('load fixture writer', () => {
  it.runIf(process.env.WRITE_LOAD_FIXTURE === '1')('writes scripts/load/data/cohort-<size>/ (npm run gen:load-cohort)', () => {
    const size = Number(process.env.LOAD_COHORT_SIZE ?? DEFAULT_LOAD_COHORT.patients)
    const options = loadCohortOptions(size)
    const cohort = buildLoadCohort(options)
    const dir = resolve(DATA_ROOT, `cohort-${size}`)
    mkdirSync(dir, { recursive: true })
    for (const name of readdirSync(dir)) {
      if (name.startsWith('chunk-')) unlinkSync(resolve(dir, name))
    }
    cohort.chunks.forEach((chunk, i) => {
      writeFileSync(resolve(dir, `chunk-${String(i + 1).padStart(4, '0')}.json`), JSON.stringify(chunk))
    })
    const expected = {
      type: 'load-cohort',
      measureName: 'DiabetesHbA1cRate',
      seed: options.seed,
      patientCount: size,
      chunkPatients: options.chunkPatients,
      chunkCount: cohort.chunks.length,
      period: options.period,
      checkpoints: cohort.checkpoints.map((cp) => ({
        patients: cp.patients,
        chunks: cp.chunks,
        resources: cp.resources,
        populations: {
          'initial-population': cp.oracle.initialPopulation,
          denominator: cp.oracle.denominator,
          numerator: cp.oracle.numerator,
        },
        score: cp.oracle.score,
      })),
      scoreTolerance: 0.01,
      samplePatientIds: cohort.samplePatientIds,
      stats: cohort.stats,
      _note: 'PAT-266 load-test cohort: generated by frontend/src/utils/loadCohort.ts from the TW Core patient generator with a fixed seed; each checkpoint carries the oracle (populations / score of the demo measure DiabetesHbA1cRate) for the first N patients, computed in TypeScript. Regenerate with `npm run gen:load-cohort -- <size>` in frontend/.',
    }
    writeFileSync(resolve(dir, 'expected.json'), JSON.stringify(expected, null, 2) + '\n')
    expect(existsSync(resolve(dir, 'expected.json'))).toBe(true)
  })
})
