#!/usr/bin/env bash
# Smoke push-worker GET /health (host :9193 by default).
set -euo pipefail

HEALTH_URL="${PUSH_HEALTH_URL:-http://localhost:9193/health}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      echo "Usage: $0"
      echo "Env: PUSH_HEALTH_URL (default http://localhost:9193/health)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

body=$(curl -fsS "$HEALTH_URL") || {
  echo "[FAIL] push-worker health $HEALTH_URL" >&2
  exit 1
}
body_trim=$(echo -n "$body" | tr -d '\r\n')
[[ "$body_trim" == "ok" ]] || {
  echo "[FAIL] expected ok, got: $body" >&2
  exit 1
}
echo "[OK] push-worker health ($HEALTH_URL)"
