#!/usr/bin/env bash
# OpenAPI contains export-compliance-prep (core-api must be running).
# Download to a temp file — do not pipe curl|grep -q under pipefail (SIGPIPE → curl 23).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

for p in /api/openapi.json /api/openapi.yaml; do
  if curl -fsS "${BASE_URL}${p}" -o "$tmp" \
    && grep -q export-compliance-prep "$tmp"; then
    echo "[OK] export-compliance-prep in OpenAPI at ${p}" >&2
    exit 0
  fi
done
echo "[FAIL] export-compliance-prep not found (core-api on ${BASE_URL}?)" >&2
exit 1
