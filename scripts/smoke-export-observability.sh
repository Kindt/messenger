#!/usr/bin/env bash
# Verifies Prometheus metrics on core-api, export-replay, and retention workers.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CORE_URL="${CORE_METRICS_URL:-http://localhost:8080/api/v1/metrics/prometheus}"
WORKER_URL="${WORKER_METRICS_URL:-http://localhost:9193/metrics}"
RET_URL="${RETENTION_METRICS_URL:-http://localhost:9192/metrics}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-prometheus.sh
source "$SCRIPT_DIR/lib/smoke-prometheus.sh"

fetch() {
  echo "GET $1" >&2
  curl -fsS "$1"
}

echo "Core API export metrics ..." >&2
core="$(fetch "$CORE_URL")"
prometheus_metric_present "$core" export_jobs_enqueued_total || fail "missing export_jobs_enqueued_total"
prometheus_metric_present "$core" export_jobs_processing_stale || fail "missing export_jobs_processing_stale"
echo "[OK] core-api export metrics" >&2

echo "Export-replay worker ..." >&2
worker="$(fetch "$WORKER_URL")"
prometheus_metric_present "$worker" export_replay_worker_jobs_started_total || fail "missing worker started"
echo "[OK] export-replay worker metrics" >&2

echo "Retention worker ..." >&2
ret="$(fetch "$RET_URL")"
prometheus_metric_present "$ret" retention_worker_export_suggested_published_total || fail "missing export_suggested"
echo "[OK] retention export metrics" >&2
echo "[OK] export observability smoke" >&2
