#!/usr/bin/env bash
# export-replay /metrics + optional cancel flow counter bump.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

WORKER_URL="${WORKER_METRICS_URL:-http://localhost:9193/metrics}"
CORE_URL="${CORE_METRICS_URL:-http://localhost:8080/api/v1/metrics/prometheus}"
CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
SKIP_CANCEL=false
SKIP_CORE=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-prometheus.sh
source "${SCRIPT_DIR}/lib/smoke-prometheus.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --worker-url) WORKER_URL="$2"; shift 2 ;;
    --core-url) CORE_URL="$2"; shift 2 ;;
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --skip-cancel) SKIP_CANCEL=true; shift ;;
    --skip-core) SKIP_CORE=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--worker-url URL] [--skip-cancel]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

fetch() {
  curl -fsS "$1"
}

echo "Worker metrics ..." >&2
worker_before=$(fetch "$WORKER_URL") || fail "worker metrics"
prometheus_metric_present "$worker_before" "export_replay_worker_jobs_cancelled_total" || fail "missing worker cancelled metric"
cancelled_before=$(prometheus_counter "$worker_before" "export_replay_worker_jobs_cancelled_total")
cancelled_before="${cancelled_before:-0}"
echo "[OK] worker metrics (cancelled=$cancelled_before)" >&2

if ! $SKIP_CORE; then
  echo "Core API metrics ..." >&2
  core=$(fetch "$CORE_URL") || fail "core metrics"
  prometheus_metric_present "$core" "export_jobs_cancelled_total" || fail "missing api cancelled metric"
  echo "[OK] core-api export metrics" >&2
fi

if $SKIP_CANCEL || [[ -z "$CHAT_ID" ]]; then
  [[ -z "$CHAT_ID" ]] && echo "[SKIP] cancel flow (use --chat-id)" >&2
  exit 0
fi

echo "Admin request+cancel ..." >&2
"$SCRIPT_DIR/smoke-admin-export-request-cancel.sh" --chat-id "$CHAT_ID" --url "$BASE_URL" --skip-audit
sleep 2
worker_after=$(fetch "$WORKER_URL") || fail "worker metrics after"
cancelled_after=$(prometheus_counter "$worker_after" "export_replay_worker_jobs_cancelled_total")
cancelled_after="${cancelled_after:-0}"
if awk -v a="$cancelled_before" -v b="$cancelled_after" 'BEGIN { exit (b > a) ? 0 : 1 }'; then
  echo "[OK] worker cancelled counter: $cancelled_before -> $cancelled_after" >&2
else
  echo "[WARN] cancelled counter did not increase ($cancelled_before -> $cancelled_after)" >&2
fi
