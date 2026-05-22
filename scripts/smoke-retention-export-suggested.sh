#!/usr/bin/env bash
# E2E: retention-worker pass -> msg.export.suggested -> core-api audit.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

BASE_URL="${BASE_URL:-http://localhost:8080}"
RETENTION_METRICS_URL="${RETENTION_METRICS_URL:-http://localhost:9192/metrics}"
CHAT_ID="${CHAT_ID:-}"
WAIT_SECONDS="${WAIT_SECONDS:-180}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-5}"
PREPARE=false
SEED=false
CREATE_GROUP=false
INCLUDE_FILE=false
MESSAGE_COUNT="${MESSAGE_COUNT:-3}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-prometheus.sh
source "$SCRIPT_DIR/lib/smoke-prometheus.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --prepare) PREPARE=true; shift ;;
    --seed) SEED=true; shift ;;
    --create-group) CREATE_GROUP=true; shift ;;
    --include-file) INCLUDE_FILE=true; shift ;;
    --count) MESSAGE_COUNT="$2"; shift 2 ;;
    --retention-metrics-url) RETENTION_METRICS_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--seed] [--create-group] [--prepare]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

if $SEED; then
  seed_args=(--url "$BASE_URL" --count "$MESSAGE_COUNT")
  [[ -n "$CHAT_ID" ]] && seed_args+=(--chat-id "$CHAT_ID")
  $CREATE_GROUP && seed_args+=(--create-group)
  $PREPARE && seed_args+=(--prepare)
  $INCLUDE_FILE && seed_args+=(--include-file)
  CHAT_ID="$("${SCRIPT_DIR}/seed-retention-hot-body-candidates.sh" "${seed_args[@]}" | tail -n 1)"
elif $PREPARE && [[ -n "$CHAT_ID" ]]; then
  "${SCRIPT_DIR}/prepare-retention-export-smoke.sh" --chat-id "$CHAT_ID" --url "$BASE_URL"
fi

retention_root="${RETENTION_METRICS_URL%/metrics}"
echo "GET ${retention_root}/health" >&2
curl -fsS "${retention_root}/health" | grep -qi ok || fail "retention /health"
curl -fsS "${retention_root}/metrics" | grep -q retention_worker || fail "retention /metrics"

metrics_uri="${RETENTION_METRICS_URL}"
[[ "$metrics_uri" == */metrics ]] || metrics_uri="${metrics_uri%/}/metrics"
baseline_text="$(curl -fsS "$metrics_uri")"
baseline_epoch="$(prometheus_gauge "$baseline_text" retention_worker_last_hot_body_pass_epoch_seconds || true)"
[[ -n "$baseline_epoch" ]] || baseline_epoch=0

echo "Waiting for retention hot-body pass (baseline epoch=$baseline_epoch) ..." >&2
deadline=$((SECONDS + WAIT_SECONDS))
pass_seen=false
while (( SECONDS < deadline )); do
  sleep "$POLL_INTERVAL_SEC"
  text="$(curl -fsS "$metrics_uri")"
  epoch="$(prometheus_gauge "$text" retention_worker_last_hot_body_pass_epoch_seconds || true)"
  [[ -n "$epoch" ]] || epoch=0
  if awk -v e="$epoch" -v b="$baseline_epoch" 'BEGIN { exit !(e > b) }'; then
    echo "[OK] retention pass epoch=$epoch" >&2
    pass_seen=true
    break
  fi
  echo "  ... waiting (epoch=$epoch)" >&2
done

$pass_seen || fail "Timed out; run retention-export-smoke-up first"

text="$(curl -fsS "$metrics_uri")"
if echo "$text" | grep -q retention_worker_export_suggested_published_total; then
  pub="$(prometheus_counter "$text" retention_worker_export_suggested_published_total || true)"
  if [[ -n "$pub" ]] && awk "BEGIN { exit !($pub > 0) }"; then
    echo "[OK] retention_worker_export_suggested_published_total=$pub" >&2
  else
    echo "[WARN] export_suggested metric 0 (no candidates?)" >&2
  fi
fi

suggest_args=(--url "$BASE_URL")
[[ -n "$CHAT_ID" ]] && suggest_args+=(--chat-id "$CHAT_ID")
"${SCRIPT_DIR}/smoke-export-suggested.sh" "${suggest_args[@]}" || {
  echo "[HINT] Use --seed --create-group --prepare for a fresh chat." >&2
  exit 1
}
echo "[OK] retention -> export.suggested audit" >&2
