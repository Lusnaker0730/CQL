# Secrets Rotation Guide

## Overview

All secrets are managed via Bitnami Sealed Secrets. Plaintext values live in `scripts/secret-values.env` (never committed) and are sealed into `k8s/secrets.yml` (safe to commit).

## Rotation Procedure

1. **Update plaintext values** in `scripts/secret-values.env`
2. **Re-seal** the secrets:
   ```bash
   ./scripts/seal-secrets.sh
   ```
3. **Commit** the updated `k8s/secrets.yml`
4. **Apply** to the cluster:
   ```bash
   kubectl apply -f k8s/secrets.yml
   ```
5. **Restart pods** to pick up new secret values:
   ```bash
   kubectl rollout restart deployment/backend -n cql-platform
   kubectl rollout restart deployment/frontend -n cql-platform
   ```
6. **Verify** pods are healthy:
   ```bash
   kubectl rollout status deployment/backend -n cql-platform --timeout=120s
   ```

## Recommended Rotation Schedule

| Secret | Rotation Period | Notes |
|--------|----------------|-------|
| `DB_USERNAME` / `DB_PASSWORD` | 90 days | Coordinate with PostgreSQL role update |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | 90 days | Must match DB_USERNAME/DB_PASSWORD |
| `JWT_SECRET` | 180 days | Active sessions will be invalidated |
| `ENCRYPTION_KEY` | 365 days | **See special procedure below** |
| `GF_SECURITY_ADMIN_PASSWORD` | 90 days | Grafana admin login |
| `VSAC_API_KEY` | Per VSAC policy | Check VSAC account expiration |
| `OKTA_CLIENT_ID` | Rarely | Only when rotating Okta app |
| `OKTA_CLIENT_SECRET` | 180 days | Regenerate in Okta admin console |
| `OKTA_ISSUER` | Rarely | Only if Okta org/auth server changes |
| `METRICS_SCRAPE_PASSWORD` | 90 days | **See special procedure below** (must stay in sync between backend + prometheus containers) |

## Special: ENCRYPTION_KEY Rotation

The `ENCRYPTION_KEY` is used to encrypt sensitive data at rest in the database. Rotating it requires re-encrypting all existing data:

1. Deploy new code that supports dual-key decryption (old + new key)
2. Run the re-encryption migration job
3. Verify all data is re-encrypted with the new key
4. Remove the old key from configuration
5. Rotate the secret as per normal procedure

**Do not rotate ENCRYPTION_KEY without completing the re-encryption step**, or encrypted data will become unreadable.

## Special: METRICS_SCRAPE_PASSWORD Rotation (PAT-113)

This secret protects `/actuator/prometheus` with HTTP Basic auth. It is consumed by
**two containers simultaneously** (`backend` signs auth, `prometheus` scrape sends it),
so a mid-rotation window where backend has the new password but prometheus has the old
(or vice versa) causes scrape failures and the `ServiceDown` alert fires. The rotation
must restart both containers in one shot.

### VM (docker-compose) procedure

```bash
# 1. SSH in
ssh root@<VM>

# 2. Generate new secret
NEW=$(openssl rand -base64 24)
# Intentionally NOT echoed — prevents the password leaking into shell history

# 3. Update .env atomically (sed -i in-place, no stdout echo)
cd /opt/CQL/docker
sed -i "/^METRICS_SCRAPE_PASSWORD=/d" .env
echo "METRICS_SCRAPE_PASSWORD=$NEW" >> .env
unset NEW   # scrub from shell env

# 4. Force-recreate both containers (MUST be done together)
docker compose -f docker-compose.yml up -d --force-recreate prometheus backend

# 5. Verify scrape resumes
sleep 15
docker exec docker-prometheus-1 sh -c \
  "wget -q -O- 'http://localhost:9090/api/v1/query?query=up{job=\"cql-platform\"}'" \
  | jq '.data.result[0].value[1]'
# Expected: "1"
```

### Kubernetes procedure

Update both the backend and prometheus deployments in the same sealed-secrets bundle,
apply, then:
```bash
kubectl rollout restart deployment/backend deployment/prometheus -n cql-platform
```

### Safety notes

- **Never paste the password on the command line as an argument to `curl -u` or
  `wget --password=` to verify** — BusyBox (alpine containers) prints unknown flags
  with their values to stderr, leaking the secret into logs. Trust the Prometheus
  `up{job="cql-platform"}=1` signal instead.
- If the rotation fails halfway (backend restarts but prometheus doesn't), the
  `ServiceDown` alert will fire. This is loud by design. Complete the rotation or
  roll back both.
- The password is present in `docker inspect` output — treat the VM's docker socket
  as secret-sensitive.

## Special: JWT_SECRET Rotation

Rotating `JWT_SECRET` invalidates all active user sessions. Plan for:

- Off-peak rotation window
- Notify users of forced re-login
- Monitor authentication error rates post-rotation

## Emergency Rotation

If a secret is compromised:

1. Immediately update `scripts/secret-values.env` with new values
2. Run `./scripts/seal-secrets.sh`
3. Apply directly: `kubectl apply -f k8s/secrets.yml`
4. Force restart: `kubectl rollout restart deployment/backend -n cql-platform`
5. Investigate the breach and audit access logs
