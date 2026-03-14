# Rollback Runbook

## Automatic Rollback

The deploy workflow (`deploy.yml`) performs automatic rollback on production deployment failure:

1. After `kubectl apply`, the workflow runs `kubectl rollout status --timeout=300s`
2. If the rollout times out or fails, it automatically executes `kubectl rollout undo`
3. A Slack notification is sent with the failure details

## Manual Rollback

### Rollback to Previous Version

```bash
# Undo the last deployment
kubectl rollout undo deployment/backend -n cql-platform
kubectl rollout undo deployment/frontend -n cql-platform

# Verify rollback
kubectl rollout status deployment/backend -n cql-platform --timeout=120s
kubectl rollout status deployment/frontend -n cql-platform --timeout=120s
```

### Rollback to a Specific Revision

```bash
# List revision history
kubectl rollout history deployment/backend -n cql-platform

# Rollback to a specific revision
kubectl rollout undo deployment/backend -n cql-platform --to-revision=3
```

### Rollback by Image Tag

```bash
# Set a known-good image directly
kubectl set image deployment/backend \
  backend=ghcr.io/OWNER/cql-platform/backend:sha-abc1234 \
  -n cql-platform

kubectl set image deployment/frontend \
  frontend=ghcr.io/OWNER/cql-platform/frontend:sha-abc1234 \
  -n cql-platform
```

## Database Migration Rollback

Flyway migrations are forward-only by default. To undo a migration:

1. **Create a reverse migration** file `V{N+1}__undo_V{N}_description.sql`
2. Write SQL to reverse the schema changes (DROP columns, restore old constraints, etc.)
3. Deploy the reverse migration through the normal pipeline
4. **Never** delete or modify an existing migration file

### Example

If `V33__sandbox_presets.sql` needs reverting:

```sql
-- V34__undo_V33_sandbox_presets.sql
DROP TABLE IF EXISTS sandbox_presets;
```

### Caution

- Migrations that drop columns or tables **cannot be reversed** if data is lost
- Always back up the database before applying risky migrations
- Test reverse migrations in staging first

## Staging Namespace

Apply manifests to staging for pre-production validation:

```bash
./scripts/apply-staging.sh
```

This replaces the namespace from `cql-platform` to `cql-platform-staging` in all manifests and applies them.

## Verification Checklist

After any rollback:

- [ ] All pods are Running: `kubectl get pods -n cql-platform`
- [ ] Health check passes: `curl https://<host>/actuator/health`
- [ ] No error spikes in logs: `kubectl logs -l app=backend -n cql-platform --tail=50`
- [ ] Frontend loads correctly: `curl -s -o /dev/null -w "%{http_code}" https://<host>/`
- [ ] Database connectivity: check `/actuator/health` shows db status UP
