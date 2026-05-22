#!/usr/bin/env bash
# Wait for core-api, retention-worker, and export-replay-worker readiness.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

BASE_URL="${BASE_URL:-http://localhost:8080}"
RETENTION_BASE_URL="${RETENTION_BASE_URL:-http://localhost:9192}"
EXPORT_REPLAY_BASE_URL="${EXPORT_REPLAY_BASE_URL:-http://localhost:9193}"
SKIP_EXPORT_REPLAY=false
TIMEOUT_SEC="${TIMEOUT_SEC:-300}"
INTERVAL_SEC="${INTERVAL_SEC:-5}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-export-replay) SKIP_EXPORT_REPLAY=true; shift ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--skip-export-replay]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

deadline=$((SECONDS + TIMEOUT_SEC))
core_ok=false
ret_ok=false
export_ok=false
$SKIP_EXPORT_REPLAY && export_ok=true

while (( SECONDS < deadline )); do
  if ! $core_ok && curl -fsS --max-time 5 "${BASE_URL}/api/v1/health" >/dev/null 2>&1; then
    echo "[OK] core-api health" >&2
    core_ok=true
  fi
  if ! $ret_ok && curl -fsS --max-time 5 "${RETENTION_BASE_URL}/health" 2>/dev/null | grep -qi ok; then
    echo "[OK] retention-worker health" >&2
    ret_ok=true
  fi
  if ! $export_ok && curl -fsS --max-time 5 "${EXPORT_REPLAY_BASE_URL}/health" 2>/dev/null | grep -qi ok; then
    echo "[OK] export-replay-worker health" >&2
    export_ok=true
  fi
  if $core_ok && $ret_ok && $export_ok; then
    exit 0
  fi
  echo "  waiting core=$core_ok retention=$ret_ok export=$export_ok ..." >&2
  sleep "$INTERVAL_SEC"
done

fail "Timed out (core=$core_ok retention=$ret_ok export=$export_ok)"
