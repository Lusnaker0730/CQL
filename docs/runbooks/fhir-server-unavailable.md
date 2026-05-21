# Runbook: FHIR Server Unavailable

_Last updated: 2026-04-24 (PAT-110 / PAT-111 / PAT-112 stack)_

## What you will see

### User-facing signals (PAT-110)
- **Yellow persistent banner at the top of the app**: "EHR 'X' is temporarily unavailable". Does NOT auto-dismiss; user clicks to acknowledge.
- HTTP 503 with structured body on affected endpoints:
  ```json
  {
    "errorType": "FHIR_UPSTREAM_UNAVAILABLE",
    "upstream": {
      "connectionId": 42,
      "connectionName": "Taipei General FHIR",
      "reason": "CIRCUIT_BREAKER_OPEN",
      "retryAfterSeconds": 60
    },
    "message": "FHIR service is temporarily unavailable due to repeated failures."
  }
  ```
- `Retry-After` header set to 30 or 60 seconds.

### Admin-facing signals (PAT-111)
- `EhrConnectionList` table (admin settings) shows the affected connection with a **red "Down" chip** + availability %. Polls every 30s.
- Hover tooltip shows: 24h availability, avg response ms, error count 24h, last checked timestamp.

### Behavior differences from earlier versions (PAT-112a/b/c)
- **CDS Hooks**: on FHIR outage, returns 5xx. Does **not** return an empty `cards: []` response — an empty card would look identical to "no alert needed" and hide the outage from clinicians.
- **Measure evaluation**: on FHIR outage during any patient in a batch, the entire evaluation aborts with `errorResult`. Does **not** partial-aggregate — a partial denominator falsifies the quality score.
- **Prefetch parse failures** (PAT-112b): any single bad prefetch key fails the whole CDS invocation. The failed key is still recorded in `diagnostics.prefetchStatus[*].status=failed` so the EHR integration team can see which key broke.
- **HAPI 200-with-OperationOutcome-error** (PAT-112c): detected and treated as outage, not as empty data.

## Alerts that fire

| Alert | Severity | Condition | Runbook action |
|-------|----------|-----------|----------------|
| `FhirCircuitBreakerOpen` | critical | any breaker `state=open` for >1m | this runbook |
| `HighFhirRetryRate` | warning | `rate(resilience4j_retry_calls_total{kind="successful_with_retry"}[5m]) > 0.5` for 3m | upstream degrading; monitor |
| `HighErrorRate` | critical | 5xx > 5% for 2m | check whether it's FHIR-upstream 503s (expected during outage) vs. genuine backend bugs |
| `ServiceDown` | critical | `up{job="cql-platform"}=0` | check Prometheus scrape auth (PAT-113) before assuming backend is dead |

## Diagnosis

### 1. Is it CQL Platform or the FHIR server?
```bash
# Admin EHR table — single glance tells you which connection
# Or via API:
curl -s -H "Authorization: Bearer <admin-token>" \
  https://twcql.com/api/ehr/health/overview | jq '.[] | {connectionName, currentStatus, availability24h, lastCheckedAt}'
```

Output like `{"currentStatus": "down", "availability24h": 15.0}` → upstream is down. Backend is fine.

### 2. Which EHR connection, which reason?
Read a recent error envelope:
```bash
docker logs docker-backend-1 --tail 500 | grep "FhirServerUnavailableException" | tail -5
```
The log message includes the classified `Reason` (TIMEOUT / CIRCUIT_BREAKER_OPEN / UPSTREAM_5XX / CONNECTION_REFUSED / OTHER) and the `connectionId` at the time of throw.

### 3. Circuit-breaker state
```bash
# In-process metrics — requires basic auth (PAT-113)
docker exec docker-prometheus-1 sh -c "wget -q -O- 'http://localhost:9090/api/v1/query?query=resilience4j_circuitbreaker_state'" | jq '.data.result'
```
Or query it inline from Grafana's "Resilience4j" dashboard.

### 4. Raw FHIR probe
```bash
# From the backend container (same network path as the real retrieve):
docker exec docker-backend-1 sh -c "wget -q -O- --spider http://hapi-fhir:8080/fhir/metadata"
# External EHR:
docker exec docker-backend-1 sh -c "wget -q -O- --spider '<EHR_URL>/metadata'"
```

### 5. HAPI-side OperationOutcome (PAT-112c)
If the metric shows 5xx from CDS/measure but a raw FHIR probe returns 200, suspect HAPI wrapping errors in a 200 + `OperationOutcome(severity=error)`:
```bash
docker logs docker-backend-1 --tail 200 | grep "Upstream returned OperationOutcome error" 
```
If found, the upstream is misbehaving — not us.

## Resolution

### Upstream EHR is down
- **Do not restart backend.** The circuit breaker will auto-transition OPEN → HALF_OPEN → CLOSED when upstream recovers (wait `waitDurationInOpenState`: 30s for `fhirDataProvider`, 60s for `fhirTerminology`).
- Tell affected users the yellow banner is expected; they should retry after the `Retry-After` hint (30–60s).
- If upstream stays down >30 min, coordinate with the EHR ops team; consider temporarily routing to a fallback EHR via `FHIR_SERVER_URL` env change + backend restart.

### HAPI is running but throwing OperationOutcome errors
- PAT-112c now surfaces these as outages (previously silent). Check HAPI logs:
  ```bash
  docker logs --tail 200 docker-hapi-fhir-1
  ```
- Typical causes: HAPI DB timeout, index corruption, JVM GC storm. Restart HAPI:
  ```bash
  docker compose -f /opt/CQL/docker/docker-compose.yml restart hapi-fhir
  ```

### Prefetch parse failures (CDS-only, PAT-112b)
Symptom: CDS returns 5xx on a specific hook, `diagnostics.prefetchStatus[N].status=failed` in the response body.
- Root cause is usually EHR-sent JSON that the backend can't deserialize (FHIR version mismatch, custom profile, or charset issue).
- Look at the `error` field inside `prefetchStatus` for the exception class + message:
  ```bash
  docker logs docker-backend-1 --tail 200 | grep "Prefetch key .* failed to parse"
  ```
- Fix is on the EHR side (correct the prefetch payload). Backend does not retry.

### Partial measure evaluation aborts (PAT-112a)
Symptom: `POST /api/measures/{id}/$evaluate-measure` returns a result with `status: "error"` and a message "Measure evaluation aborted: upstream FHIR server unavailable during N of M patient evaluations."
- Design: partial denominators falsify the quality score. The evaluation aborts intentionally once any patient's retrieve fails due to FHIR outage.
- Resolution: wait for the upstream to recover, then re-trigger evaluation. No data is partially persisted.

### Manual circuit-breaker reset
Not needed — Resilience4j transitions automatically. If you need to force it (e.g. during a known-good post-incident test), restart the backend:
```bash
docker compose -f /opt/CQL/docker/docker-compose.yml restart backend
```
This resets all breakers to CLOSED and re-opens them on the first probe failure.

## Prevention

- Grafana dashboard panels to watch: "HikariCP Connection Pool", "Patient Import Pool", "CQL Execution Pool", "Resilience4j State".
- `EhrConnectionList` admin UI (PAT-111) gives always-on visibility into per-connection health; schedule it as part of daily check.
- For EHRs known to be flaky, lower `RATE_LIMIT_CDS_DISCOVERY_RPM` (PAT-107, default 20) to cut enumeration noise.
- Keep an eye on `measure_report_deserialization_failures` (PAT-108) — unrelated to FHIR outage but fires if historical reports become unreadable, which is a separate silent-data-loss class.

## Related

- `docs/pre-launch-production-readiness-review.md` §#10 — the product/clinical decision record
- `DEPLOYMENT_GUIDE_zh-TW.md` §4.5 — Prometheus scrape auth setup (PAT-113)
- `docs/API.md` — `FHIR_UPSTREAM_UNAVAILABLE` envelope shape
- `docs/secrets-rotation.md` — `METRICS_SCRAPE_PASSWORD` rotation
