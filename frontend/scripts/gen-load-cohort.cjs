#!/usr/bin/env node
// PAT-266: write the k6 load-test cohort (scripts/load/data/cohort-<size>/) from the TW Core patient
// generator with the fixed seed in src/utils/loadCohort.ts.
// `npm run gen:load-cohort [-- <size>]` — size defaults to 10000 and must be a positive multiple of 100.
// scripts/load/run.sh calls this when the fixture for its LOAD_COHORT_SIZE is missing.
const { spawnSync } = require('node:child_process')

const size = process.argv[2] || process.env.LOAD_COHORT_SIZE || '10000'
if (!/^\d+$/.test(size) || Number(size) === 0 || Number(size) % 100 !== 0) {
  console.error(`cohort size must be a positive multiple of 100, got ${size}`)
  process.exit(2)
}

const nodeOptions = [process.env.NODE_OPTIONS, '--max-old-space-size=4096'].filter(Boolean).join(' ')
const result = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['vitest', 'run', 'src/utils/__tests__/loadCohort.test.ts'],
  {
    stdio: 'inherit',
    env: { ...process.env, WRITE_LOAD_FIXTURE: '1', LOAD_COHORT_SIZE: size, NODE_OPTIONS: nodeOptions },
    shell: process.platform === 'win32',
  },
)
process.exit(result.status ?? 1)
