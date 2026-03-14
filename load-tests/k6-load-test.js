import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const cqlTranslateDuration = new Trend('cql_translate_duration');
const cqlExecuteDuration = new Trend('cql_execute_duration');
const fhirSearchDuration = new Trend('fhir_search_duration');

export const options = {
    stages: [
        { duration: '1m', target: 20 },   // ramp up to 20 users
        { duration: '3m', target: 20 },   // hold at 20
        { duration: '1m', target: 50 },   // ramp to 50
        { duration: '3m', target: 50 },   // hold at 50
        { duration: '1m', target: 100 },  // ramp to 100
        { duration: '3m', target: 100 },  // hold at 100
        { duration: '2m', target: 0 },    // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        errors: ['rate<0.05'],
    },
};

const SIMPLE_CQL = `
library TestLibrary version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

define "Is Adult":
  AgeInYears() >= 18

define "Has Name":
  Patient.name.exists()
`;

export default function () {
    const scenario = Math.random();

    if (scenario < 0.3) {
        testHealthCheck();
    } else if (scenario < 0.55) {
        testCqlTranslate();
    } else if (scenario < 0.75) {
        testCqlValidate();
    } else if (scenario < 0.9) {
        testFhirSearch();
    } else {
        testCqlExecute();
    }

    sleep(Math.random() * 2 + 0.5);
}

function testHealthCheck() {
    const res = http.get(`${BASE_URL}/actuator/health`);
    check(res, {
        'health check status is 200': (r) => r.status === 200,
    });
    errorRate.add(res.status !== 200);
}

function testCqlTranslate() {
    const payload = JSON.stringify({ cql: SIMPLE_CQL });
    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${BASE_URL}/api/cql/translate`, payload, params);

    cqlTranslateDuration.add(res.timings.duration);
    check(res, {
        'translate status is 200': (r) => r.status === 200,
    });
    errorRate.add(res.status !== 200);
}

function testCqlValidate() {
    const payload = JSON.stringify({ cql: SIMPLE_CQL });
    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${BASE_URL}/api/cql/validate`, payload, params);

    check(res, {
        'validate status is 200': (r) => r.status === 200,
    });
    errorRate.add(res.status !== 200);
}

function testCqlExecute() {
    const payload = JSON.stringify({
        cql: SIMPLE_CQL,
        patientId: 'example',
    });
    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${BASE_URL}/api/cql/execute`, payload, params);

    cqlExecuteDuration.add(res.timings.duration);
    check(res, {
        'execute status is 200 or 500': (r) => r.status === 200 || r.status === 500,
    });
    errorRate.add(res.status >= 500);
}

function testFhirSearch() {
    const res = http.get(`${BASE_URL}/api/fhir/resources/Patient`);

    fhirSearchDuration.add(res.timings.duration);
    check(res, {
        'fhir search status is 200': (r) => r.status === 200,
    });
    errorRate.add(res.status !== 200);
}
