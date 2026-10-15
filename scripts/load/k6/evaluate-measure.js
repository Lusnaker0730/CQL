// PAT-266 — k6 load test for the CQL Platform (run by scripts/load/run.sh inside the grafana/k6 image).
//
// Two profiles, selected with -e PROFILE=…:
//   scale        one virtual user evaluates the demo measure over the whole seeded cohort a few times
//                (CHECKPOINT patients); every answer must equal the oracle in expected.json
//   concurrency  EVAL_VUS users evaluate the measure concurrently while EXECUTE_VUS users run single-patient
//                CQL (the editor's hot path) and BROWSE_VUS users browse the measure list — for DURATION
//
// Environment (all optional except CHECKPOINT): API_BASE, FHIR_BASE, EXPECTED (path to expected.json),
// CHECKPOINT (patients seeded), ITERATIONS, EVAL_VUS, EXECUTE_VUS, BROWSE_VUS, DURATION, EVAL_TIMEOUT,
// LOAD_USER / LOAD_PASSWORD (the seeded admin by default).
import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Counter, Trend } from 'k6/metrics'

const API = (__ENV.API_BASE || 'http://backend:8080/api').replace(/\/$/, '')
const FHIR = (__ENV.FHIR_BASE || 'http://hapi-fhir:8080/fhir').replace(/\/$/, '')
const PROFILE = __ENV.PROFILE || 'scale'
const CHECKPOINT = Number(__ENV.CHECKPOINT || 0)
const EXPECTED = JSON.parse(open(__ENV.EXPECTED || '/data/expected.json'))
const CHECK = EXPECTED.checkpoints.find((c) => c.patients === CHECKPOINT)
if (!CHECK) {
  throw new Error(`no checkpoint for ${CHECKPOINT} patients in expected.json (have ${EXPECTED.checkpoints.map((c) => c.patients).join(', ')})`)
}
const USER = __ENV.LOAD_USER || 'admin'
const PASSWORD = __ENV.LOAD_PASSWORD || 'admin'
const EVAL_TIMEOUT = __ENV.EVAL_TIMEOUT || '45m'
const DURATION = __ENV.DURATION || '3m'
const SCORE_TOLERANCE = EXPECTED.scoreTolerance || 0.01

const evaluateMs = new Trend('evaluate_measure_ms', true)
const oracleMismatches = new Counter('oracle_mismatches')
const evaluationErrors = new Counter('evaluation_errors')

const scaleOptions = {
  scenarios: {
    scale: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: Number(__ENV.ITERATIONS || 2),
      maxDuration: EVAL_TIMEOUT,
      exec: 'evaluateMeasure',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    oracle_mismatches: ['count==0'],
    evaluation_errors: ['count==0'],
  },
}

const concurrencyOptions = {
  scenarios: {
    evaluate: {
      executor: 'constant-vus',
      vus: Number(__ENV.EVAL_VUS || 3),
      duration: DURATION,
      exec: 'evaluateMeasure',
      gracefulStop: EVAL_TIMEOUT,
    },
    cql_execute: {
      executor: 'constant-vus',
      vus: Number(__ENV.EXECUTE_VUS || 5),
      duration: DURATION,
      exec: 'executeCql',
      gracefulStop: '2m',
    },
    browse: {
      executor: 'constant-vus',
      vus: Number(__ENV.BROWSE_VUS || 10),
      duration: DURATION,
      exec: 'browse',
      gracefulStop: '1m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.01'],
    oracle_mismatches: ['count==0'],
    evaluation_errors: ['count==0'],
    'http_req_duration{scenario:browse}': ['p(95)<1000'],
    'http_req_duration{scenario:cql_execute}': ['p(95)<5000'],
  },
}

export const options = Object.assign({ setupTimeout: '3m' }, PROFILE === 'concurrency' ? concurrencyOptions : scaleOptions)

function safeJson(res) {
  try {
    return res.json()
  } catch (e) {
    return null
  }
}

export function setup() {
  const login = http.post(`${API}/auth/login`, JSON.stringify({ username: USER, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  })
  if (login.status !== 200) fail(`login failed: HTTP ${login.status} ${String(login.body).slice(0, 200)}`)
  const token = login.json('token')
  if (!token) fail('login response has no token')
  const headers = { Authorization: `Bearer ${token}` }

  const list = http.get(`${API}/measures?search=${encodeURIComponent(EXPECTED.measureName)}`, { headers, tags: { name: 'measure-lookup' } })
  if (list.status !== 200) fail(`measure lookup failed: HTTP ${list.status}`)
  const measure = (list.json() || []).find((m) => m.name === EXPECTED.measureName)
  if (!measure) fail(`measure ${EXPECTED.measureName} not found (DataInitializer seeds it on a fresh database)`)

  const count = http.get(`${FHIR}/Patient?_summary=count`, { tags: { name: 'patient-count' } })
  const patients = count.status === 200 ? count.json('total') : -1
  if (patients !== CHECKPOINT) fail(`FHIR holds ${patients} patients but this run is the ${CHECKPOINT}-patient checkpoint`)

  return { token, measureId: measure.id }
}

function auth(data) {
  return { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' }
}

/** One `$evaluate-measure` over the whole seeded cohort, compared with the oracle. */
export function evaluateMeasure(data) {
  const url = `${API}/measures/${data.measureId}/$evaluate-measure?reportType=summary&periodStart=${EXPECTED.period.start}&periodEnd=${EXPECTED.period.end}`
  const res = http.post(url, null, { headers: auth(data), timeout: EVAL_TIMEOUT, tags: { name: 'evaluate-measure', patients: String(CHECKPOINT) } })
  evaluateMs.add(res.timings.duration, { patients: String(CHECKPOINT) })

  const body = safeJson(res)
  const group = body && body.groups && body.groups[0] ? body.groups[0] : null
  const populations = {}
  if (group) for (const p of group.populations || []) populations[p.populationType] = p.count
  const score = group ? group.measureScore : null
  const expectedPopulations = CHECK.populations
  const populationsMatch = Object.keys(expectedPopulations).every((k) => populations[k] === expectedPopulations[k])
  const scoreMatch = CHECK.score === null ? score === null || score === undefined
    : typeof score === 'number' && Math.abs(score - CHECK.score) <= SCORE_TOLERANCE
  const failedPatients = body && typeof body.errorCount === 'number' ? body.errorCount : 0
  const isError = res.status !== 200 || body === null || body.status === 'error' || failedPatients > 0

  check(res, {
    'evaluate: HTTP 200': (r) => r.status === 200,
    'evaluate: status is not error': () => body !== null && body.status !== 'error',
    'evaluate: no failed patients': () => failedPatients === 0,
    'evaluate: every patient evaluated': () => body !== null && (body.evaluatedPatientCount == null || body.evaluatedPatientCount === CHECKPOINT),
    'evaluate: populations match the oracle': () => populationsMatch,
    'evaluate: score matches the oracle': () => scoreMatch,
  })
  if (!populationsMatch || !scoreMatch) {
    oracleMismatches.add(1)
    console.error(`oracle mismatch at ${CHECKPOINT} patients: got ${JSON.stringify(populations)} score=${score}; expected ${JSON.stringify(expectedPopulations)} score=${CHECK.score}`)
  }
  if (isError) {
    evaluationErrors.add(1)
    const detail = body ? body.errorMessage || `errorCount=${body.errorCount}` : String(res.body).slice(0, 300)
    console.error(`evaluation error at ${CHECKPOINT} patients: HTTP ${res.status} in ${Math.round(res.timings.duration)} ms — ${detail}`)
  }
}

const EXECUTE_CQL = [
  "library LoadProbe version '1.0.0'",
  "using FHIR version '4.0.1'",
  "include FHIRHelpers version '4.0.1' called FHIRHelpers",
  'context Patient',
  'define "Conditions": Count([Condition])',
  'define "Observations": Count([Observation])',
  'define "Has Encounter": exists [Encounter]',
  '',
].join('\n')

/** The editor's hot path: translate + execute a small library for one patient. */
export function executeCql(data) {
  const ids = EXPECTED.samplePatientIds
  const patientId = ids[(__VU * 7 + __ITER) % ids.length]
  const res = http.post(`${API}/cql/execute`, JSON.stringify({ cql: EXECUTE_CQL, patientId }), {
    headers: auth(data),
    timeout: '2m',
    tags: { name: 'cql-execute' },
  })
  const body = safeJson(res)
  check(res, {
    'execute: HTTP 200': (r) => r.status === 200,
    'execute: success with results': () => body !== null && body.success === true && body.results && 'Conditions' in body.results,
  })
  sleep(0.5)
}

/** A user browsing: the measure list, one measure, the public status page. */
export function browse(data) {
  const page = http.get(`${API}/measures/page?size=20`, { headers: auth(data), tags: { name: 'measures-page' } })
  const one = http.get(`${API}/measures/${data.measureId}`, { headers: auth(data), tags: { name: 'measure-get' } })
  const status = http.get(`${API}/status`, { tags: { name: 'status' } })
  check(page, { 'browse: measure list 200': (r) => r.status === 200 })
  check(one, { 'browse: measure 200': (r) => r.status === 200 })
  check(status, { 'browse: status 200': (r) => r.status === 200 })
  sleep(1)
}
