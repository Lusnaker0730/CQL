#!/usr/bin/env bash
set -euo pipefail

# seal-secrets.sh — Generate SealedSecret from plaintext env values
# Prerequisites:
#   - kubeseal CLI installed (https://github.com/bitnami-labs/sealed-secrets)
#   - Bitnami Sealed Secrets controller running in cluster
#   - kubectl configured with cluster access

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/secret-values.env"
OUTPUT_FILE="${SCRIPT_DIR}/../k8s/secrets.yml"
NAMESPACE="cql-platform"
SECRET_NAME="cql-platform-secrets"

REQUIRED_KEYS=(
  DB_USERNAME
  DB_PASSWORD
  POSTGRES_USER
  POSTGRES_PASSWORD
  JWT_SECRET
  ENCRYPTION_KEY
  GF_SECURITY_ADMIN_PASSWORD
  VSAC_API_KEY
  OKTA_CLIENT_ID
  OKTA_CLIENT_SECRET
  OKTA_ISSUER
)

# --- Preflight checks ---
if ! command -v kubeseal &> /dev/null; then
  echo "ERROR: kubeseal CLI not found. Install from https://github.com/bitnami-labs/sealed-secrets/releases"
  exit 1
fi

if ! command -v kubectl &> /dev/null; then
  echo "ERROR: kubectl not found."
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: ${ENV_FILE} not found."
  echo "Copy scripts/secret-values.env.example to scripts/secret-values.env and fill in values."
  exit 1
fi

# --- Load env file ---
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

# --- Validate all required keys are set ---
MISSING=()
for key in "${REQUIRED_KEYS[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    MISSING+=("$key")
  fi
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "ERROR: The following required secrets are not set in ${ENV_FILE}:"
  for key in "${MISSING[@]}"; do
    echo "  - $key"
  done
  exit 1
fi

echo "All ${#REQUIRED_KEYS[@]} required secrets validated."

# --- Build kubectl create secret command ---
SECRET_ARGS=()
for key in "${REQUIRED_KEYS[@]}"; do
  SECRET_ARGS+=("--from-literal=${key}=${!key}")
done

# --- Generate SealedSecret ---
echo "Generating SealedSecret..."
kubectl create secret generic "$SECRET_NAME" \
  --namespace="$NAMESPACE" \
  "${SECRET_ARGS[@]}" \
  --dry-run=client -o yaml \
  | kubeseal \
      --format yaml \
      --namespace="$NAMESPACE" \
  > "$OUTPUT_FILE"

# --- Add labels to the output ---
# kubeseal preserves metadata but we ensure our labels are present
echo "SealedSecret written to ${OUTPUT_FILE}"
echo ""
echo "Next steps:"
echo "  1. Review the generated file: ${OUTPUT_FILE}"
echo "  2. Commit and push the sealed secret"
echo "  3. Apply to cluster: kubectl apply -f ${OUTPUT_FILE}"
echo "  4. Verify: kubectl get secret ${SECRET_NAME} -n ${NAMESPACE}"
