#!/usr/bin/env node
// PAT-255: regenerate the synthetic-cohort smoke fixture (scripts/smoke/scenarios/43-synthetic-cohort)
// from the TW Core patient generator with the fixed seed in src/utils/syntheticCohort.ts.
// `npm run gen:cohort` — portable across shells (no env-var prefix syntax needed on Windows).
const { spawnSync } = require('node:child_process')

const result = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['vitest', 'run', 'src/utils/__tests__/syntheticCohort.test.ts'],
  { stdio: 'inherit', env: { ...process.env, WRITE_SMOKE_FIXTURE: '1' }, shell: process.platform === 'win32' },
)
process.exit(result.status ?? 1)
