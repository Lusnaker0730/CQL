#!/usr/bin/env bash
set -euo pipefail

# apply-staging.sh — Apply production K8s manifests to the staging namespace
# Replaces namespace references from cql-platform → cql-platform-staging

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/.."
K8S_DIR="${PROJECT_ROOT}/k8s"
STAGING_NS="cql-platform-staging"
PROD_NS="cql-platform"

echo "=== CQL Platform — Staging Deploy ==="
echo "Target namespace: ${STAGING_NS}"
echo ""

# Ensure staging namespace exists
kubectl apply -f "${K8S_DIR}/staging/namespace.yml"

# Directories containing manifests to apply
MANIFEST_DIRS=(
  "backend"
  "frontend"
)

# Apply secrets (SealedSecret)
echo "Applying secrets..."
sed "s/namespace: ${PROD_NS}/namespace: ${STAGING_NS}/g" "${K8S_DIR}/secrets.yml" \
  | kubectl apply -f -

# Apply configmap if exists
if [[ -f "${K8S_DIR}/configmap.yml" ]]; then
  echo "Applying configmap..."
  sed "s/namespace: ${PROD_NS}/namespace: ${STAGING_NS}/g" "${K8S_DIR}/configmap.yml" \
    | kubectl apply -f -
fi

# Apply all manifests in each directory
for dir in "${MANIFEST_DIRS[@]}"; do
  FULL_DIR="${K8S_DIR}/${dir}"
  if [[ ! -d "$FULL_DIR" ]]; then
    echo "WARN: ${FULL_DIR} not found, skipping"
    continue
  fi
  echo "Applying manifests from ${dir}/..."
  for file in "${FULL_DIR}"/*.yml; do
    [[ -f "$file" ]] || continue
    echo "  $(basename "$file")"
    sed "s/namespace: ${PROD_NS}/namespace: ${STAGING_NS}/g" "$file" \
      | kubectl apply -f -
  done
done

echo ""
echo "Staging deployment complete."
echo "Verify: kubectl get pods -n ${STAGING_NS}"
