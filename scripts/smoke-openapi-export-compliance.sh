#!/usr/bin/env bash
# OpenAPI contains export-compliance-prep (core-api must be running).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
for p in /api/openapi.json /api/openapi.yaml; do
  if curl -fsS "${BASE_URL}${p}" | grep -q export-compliance-prep; then
    echo "[OK] export-compliance-prep in OpenAPI at ${p}" >&2
    exit 0
  fi
done
echo "[FAIL] export-compliance-prep not found (core-api on ${BASE_URL}?)" >&2
exit 1
