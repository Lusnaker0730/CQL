# Runbook: High Memory Usage

## Alert
`HighHeapUsage` - JVM heap usage above 85% for >5 minutes.

## Symptoms
- Slow response times across all endpoints
- Frequent GC pauses in logs
- OOM errors in container logs
- Container restarts (OOM killed)

## Diagnosis

### 1. Check JVM Heap Usage
```bash
# Current heap usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap | jq '.measurements[0].value'

# Max heap
curl -s http://localhost:8080/actuator/metrics/jvm.memory.max?tag=area:heap | jq '.measurements[0].value'
```

### 2. Check GC Activity
```bash
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
```

### 3. Check Thread Count
```bash
curl -s http://localhost:8080/actuator/metrics/jvm.threads.live | jq '.measurements[0].value'
```

### 4. Check Cache Sizes
```bash
# Caffeine cache stats
curl -s http://localhost:8080/actuator/metrics/cache.size | jq
```

### 5. Container Memory
```bash
docker stats backend --no-stream
```

## Resolution

### Immediate: Restart
If OOM is imminent:
```bash
docker compose restart backend
```

### Increase Container Memory
```yaml
# docker-compose.yml
backend:
  deploy:
    resources:
      limits:
        memory: 2G  # increase from 1G
```

### JVM Heap Tuning
Add to Dockerfile or environment:
```
JAVA_OPTS=-Xms512m -Xmx768m -XX:+UseG1GC
```

### Reduce Cache Memory
If caches are consuming too much memory, adjust Caffeine settings in `CqlConfig.java`:
- Reduce `maximumSize` from 1000
- Reduce `expireAfterWrite` from 60 minutes

### Check for Memory Leaks
1. Enable heap dump on OOM: `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp`
2. Analyze with `jmap` or VisualVM
3. Common leak sources:
   - Unclosed FHIR client connections (fixed by connection pooling)
   - Large CQL result sets held in memory
   - Accumulated translation caches

## Prevention
- Set JVM heap limits explicitly (don't rely on container defaults)
- Monitor `jvm.memory.used` and `jvm.gc.pause` metrics
- Configure appropriate cache eviction policies
- Load test with production-representative data volumes
