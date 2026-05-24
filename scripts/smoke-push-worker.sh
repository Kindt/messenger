#!/usr/bin/env bash
# Smoke push-worker GET /health (full-server :9194, dev-min :9193).
set -euo pipefail

HEALTH_URL="${PUSH_HEALTH_URL:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      echo "Usage: $0"
      echo "Env: PUSH_HEALTH_URL (if unset, probes http://localhost:9194/health then http://localhost:9193/health)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -n "$HEALTH_URL" ]]; then
  urls=("$HEALTH_URL")
else
  urls=("http://localhost:9194/health" "http://localhost:9193/health")
fi

for u in "${urls[@]}"; do
  if body=$(curl -fsS "$u"); then
    body_trim=$(echo -n "$body" | tr -d '\r\n')
    if [[ "$body_trim" == "ok" ]]; then
      echo "[OK] push-worker health ($u)"
      exit 0
    fi
  fi
done

echo "[FAIL] push-worker health checks failed for: ${urls[*]}" >&2
exit 1
