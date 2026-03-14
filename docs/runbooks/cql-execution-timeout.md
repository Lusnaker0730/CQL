# Runbook: CQL Execution Timeout

## Alert
CQL execution returns error: "CQL execution timed out after 30s"

## Symptoms
- CQL execution endpoint returns 500 with timeout message
- `cql.execution.errors` counter increasing
- Thread pool may be saturated (`cql.execution.queue.size` > 40)
- Slow response times on `/api/cql/execute`

## Diagnosis

### 1. Check Thread Pool Status
```bash
# Queue depth
curl -s http://localhost:8080/actuator/metrics/cql.execution.queue.size | jq '.measurements[0].value'

# Active threads
curl -s http://localhost:8080/actuator/metrics/cql.execution.pool.active | jq '.measurements[0].value'

# Pool size
curl -s http://localhost:8080/actuator/metrics/cql.execution.pool.size | jq '.measurements[0].value'
```

### 2. Check Execution Times
```bash
curl -s http://localhost:8080/actuator/metrics/cql.execution.duration | jq
```

### 3. Identify Slow Queries
Check application logs for slow CQL executions:
```bash
docker logs --tail 500 backend | grep "CQL execution"
```

### 4. Check FHIR Server Response Times
Slow FHIR queries are the most common cause of CQL timeouts:
```bash
docker logs --tail 200 backend | grep "FHIR"
```

## Resolution

### Immediate: Increase Timeout
If legitimate queries need more time:
```yaml
cql:
  execution:
    timeout-seconds: 60  # increase from 30
```

### CQL Query Optimization
- Reduce the scope of data retrieval (fewer date ranges, specific codes)
- Use `context Patient` to limit per-patient queries
- Avoid `retrieve` on large resource types without filters

### FHIR Server Performance
- Check HAPI FHIR server memory and CPU
- Add indexes for frequently queried search parameters
- Increase HAPI FHIR resources in docker-compose

### Thread Pool Saturation
If queue is consistently near capacity:
```yaml
cql:
  execution:
    thread-pool-size: 15   # increase core
    max-pool-size: 30      # increase max
    queue-capacity: 100    # increase queue
```

### Measure Evaluation Timeout
For measure evaluations with many patients:
- The `measure-timeout-seconds` (default 120s) controls the patient loop deadline
- Partial results are returned when timeout is reached
- Consider reducing patient cohort or running in batches

## Prevention
- Set appropriate timeouts for the expected workload
- Monitor queue depth and thread pool metrics
- Load test with representative CQL queries
- Consider caching frequently executed CQL results
