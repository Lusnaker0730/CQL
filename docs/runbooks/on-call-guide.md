# On-Call Guide

## Alert Severity Mapping

| Severity | Response Time | Examples |
|----------|--------------|---------|
| Critical | 15 minutes | ServiceDown, FhirCircuitBreakerOpen, HighErrorRate |
| Warning | 1 hour | HighLatency, HighHeapUsage, CqlQueueSaturation |

## Alert Routing

- **Critical** alerts route to the pager webhook (immediate notification)
- **Warning** alerts route to the Slack webhook (team channel)
- Alerts auto-resolve when conditions clear

## Escalation Path

1. **L1 - On-Call Engineer** (0-15 min)
   - Acknowledge alert
   - Follow relevant runbook
   - Attempt resolution

2. **L2 - Senior Engineer** (15-30 min)
   - If L1 cannot resolve
   - Complex debugging, architectural decisions

3. **L3 - Platform Lead** (30-60 min)
   - If service impact continues
   - Decisions on failover, data recovery

## Quick Reference: Common Scenarios

### Service Down
1. Check: `docker ps` - is the backend container running?
2. Check: `docker logs backend` - any startup errors?
3. Action: `docker compose restart backend`
4. Runbook: Check health endpoint at `/actuator/health`

### FHIR Server Issues
1. Check: `docker logs hapi-fhir` - is it healthy?
2. Check: Circuit breaker state in Prometheus
3. Action: Restart FHIR server if needed
4. Runbook: [fhir-server-unavailable.md](./fhir-server-unavailable.md)

### High Error Rate
1. Check: `/actuator/metrics/http.server.requests` for error breakdown
2. Check: Application logs for stack traces
3. Common causes: FHIR server down, database issues, bad input data
4. Action: Depends on root cause

### CQL Execution Timeouts
1. Check: Thread pool metrics
2. Check: FHIR server response times
3. Action: May need to increase timeout or optimize queries
4. Runbook: [cql-execution-timeout.md](./cql-execution-timeout.md)

### High Memory
1. Check: `docker stats backend`
2. Check: JVM heap metrics
3. Action: Restart if imminent OOM, increase memory limit
4. Runbook: [high-memory-usage.md](./high-memory-usage.md)

## Useful Commands

```bash
# Service status
docker compose ps

# All service logs (last 5 minutes)
docker compose logs --since 5m

# Backend health
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Circuit breaker state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Thread pool status
curl http://localhost:8080/actuator/metrics/cql.execution.queue.size
curl http://localhost:8080/actuator/metrics/cql.execution.pool.active
```

## On-Call Rotation Template

| Week | Primary | Secondary |
|------|---------|-----------|
| 1 | Engineer A | Engineer B |
| 2 | Engineer B | Engineer C |
| 3 | Engineer C | Engineer A |

Update this rotation in your team's scheduling tool.
