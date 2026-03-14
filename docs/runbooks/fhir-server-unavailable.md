# Runbook: FHIR Server Unavailable

## Alert
`FhirCircuitBreakerOpen` - Circuit breaker has been in OPEN state for >1 minute.

## Symptoms
- API returns 503 Service Unavailable for FHIR-dependent endpoints
- Circuit breaker metrics show `state=open` in Prometheus
- Logs show `Circuit breaker fallback` warnings
- CQL execution and measure evaluation fail

## Diagnosis

### 1. Check Circuit Breaker State
```bash
curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq
```

### 2. Check FHIR Server Health
```bash
# HAPI FHIR server
curl -f http://hapi-fhir:8080/fhir/metadata

# External terminology server
curl -f http://tx.fhir.org/r4/metadata
```

### 3. Check Network Connectivity
```bash
docker exec backend ping -c 3 hapi-fhir
docker exec backend wget -q --spider http://hapi-fhir:8080/fhir/metadata
```

### 4. Check FHIR Server Logs
```bash
docker logs --tail 100 hapi-fhir
```

### 5. Check Connection Pool
```bash
curl -s http://localhost:8080/actuator/metrics/http.client.requests | jq
```

## Resolution

### FHIR Server Down
1. Check if HAPI FHIR container is running: `docker ps | grep hapi`
2. Restart if needed: `docker compose restart hapi-fhir`
3. Wait for health check to pass (~30s)
4. Circuit breaker will auto-transition to HALF_OPEN after `waitDurationInOpenState` (30s for data, 60s for terminology)

### Network Issues
1. Verify Docker network: `docker network inspect cql-network`
2. Restart backend to re-establish connections: `docker compose restart backend`

### External Terminology Server
1. Check tx.fhir.org status
2. If persistently down, switch to local terminology server via `FHIR_TERMINOLOGY_URL` env var
3. Cached terminology lookups will continue working from Caffeine cache

### Manual Circuit Breaker Reset
Circuit breakers automatically transition from OPEN -> HALF_OPEN -> CLOSED. No manual intervention needed. Wait for the configured `waitDurationInOpenState`.

## Prevention
- Monitor `resilience4j_circuitbreaker_state` metric
- Set up alerts for sustained OPEN state
- Use connection pooling (configured: 20 max, 10 per route)
- Ensure FHIR server has adequate resources
