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

## Special: ENCRYPTION_KEY Rotation

The `ENCRYPTION_KEY` is used to encrypt sensitive data at rest in the database. Rotating it requires re-encrypting all existing data:

1. Deploy new code that supports dual-key decryption (old + new key)
2. Run the re-encryption migration job
3. Verify all data is re-encrypted with the new key
4. Remove the old key from configuration
5. Rotate the secret as per normal procedure

**Do not rotate ENCRYPTION_KEY without completing the re-encryption step**, or encrypted data will become unreadable.

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
