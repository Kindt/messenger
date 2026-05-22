#!/usr/bin/env bash
# Sequential export compliance smokes. ChatId optional; without --chat-id seeds a new group chat.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
WORKER_METRICS_URL="${WORKER_METRICS_URL:-http://localhost:9193/metrics}"
SKIP_SUGGEST=false
SKIP_REQUEST=false
SKIP_METRICS=false
SKIP_OBSERVABILITY=false
SKIP_GLOBAL=false
SKIP_SUGGESTED_NATS=false
SKIP_RETENTION_SUGGESTED=false
SKIP_AUTO_QUEUE_NATS=false
RETENTION_METRICS_URL="${RETENTION_METRICS_URL:-http://localhost:9192/metrics}"
SKIP_AUDIT=false
SKIP_DOWNLOAD=false
SKIP_OPENAPI=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --worker-url) WORKER_METRICS_URL="$2"; shift 2 ;;
    --skip-suggest-cancel) SKIP_SUGGEST=true; shift ;;
    --skip-request-cancel) SKIP_REQUEST=true; shift ;;
    --skip-worker-metrics) SKIP_METRICS=true; shift ;;
    --skip-observability) SKIP_OBSERVABILITY=true; shift ;;
    --skip-global-jobs) SKIP_GLOBAL=true; shift ;;
    --skip-suggested-nats) SKIP_SUGGESTED_NATS=true; shift ;;
    --skip-retention-suggested) SKIP_RETENTION_SUGGESTED=true; shift ;;
    --skip-auto-queue-nats) SKIP_AUTO_QUEUE_NATS=true; shift ;;
    --retention-metrics-url) RETENTION_METRICS_URL="$2"; shift 2 ;;
    --skip-audit) SKIP_AUDIT=true; shift ;;
    --skip-download) SKIP_DOWNLOAD=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--skip-*] ..."
      echo "  Without --chat-id: seeds via export-compliance-prep."
      echo "  --skip-download: skip final compliance flow + bundle download step."
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

AUDIT_FLAG=()
if $SKIP_AUDIT; then
  AUDIT_FLAG=(--skip-audit)
fi

step() {
  echo "" >&2
  echo "=== $1 ===" >&2
  shift
  "$@"
}

if ! $SKIP_OPENAPI; then
  step "OpenAPI export-compliance-prep" \
    env BASE_URL="$BASE_URL" "${SCRIPT_DIR}/smoke-openapi-export-compliance.sh"
fi

if [[ -z "$CHAT_ID" ]]; then
  CHAT_ID="$(step "seed compliance chat (export-compliance-prep)" \
    "${SCRIPT_DIR}/smoke-admin-export-compliance-prep.sh" --url "$BASE_URL" | sed -n 's/^CHAT_ID=//p' | tail -n 1)"
  [[ -n "$CHAT_ID" ]] || fail "prep did not return chat id"
  echo "Waiting 2s (retention SELECT age buffer) ..." >&2
  sleep 2
  echo "Using chat $CHAT_ID" >&2
fi

if ! $SKIP_SUGGEST; then
  step "suggest -> export -> cancel" \
    "${SCRIPT_DIR}/smoke-export-suggest-cancel-flow.sh" --chat-id "$CHAT_ID" --url "$BASE_URL" "${AUDIT_FLAG[@]}"
fi

if ! $SKIP_REQUEST; then
  step "admin request -> cancel" \
    "${SCRIPT_DIR}/smoke-admin-export-request-cancel.sh" --chat-id "$CHAT_ID" --url "$BASE_URL" "${AUDIT_FLAG[@]}"
fi

if ! $SKIP_SUGGESTED_NATS; then
  step "NATS export.suggested -> audit" \
    "${SCRIPT_DIR}/smoke-export-suggested-nats.sh" --chat-id "$CHAT_ID" --url "$BASE_URL"
fi

if ! $SKIP_RETENTION_SUGGESTED; then
  step "retention export.suggested -> audit" \
    "${SCRIPT_DIR}/smoke-retention-export-suggested.sh" --url "$BASE_URL" \
    --chat-id "$CHAT_ID" --retention-metrics-url "$RETENTION_METRICS_URL"
fi

if ! $SKIP_AUTO_QUEUE_NATS; then
  step "NATS export.suggested -> auto-queue" \
    "${SCRIPT_DIR}/smoke-export-auto-queue-nats.sh" --chat-id "$CHAT_ID" --url "$BASE_URL"
fi

if ! $SKIP_GLOBAL; then
  step "admin global export jobs" \
    env BASE_URL="$BASE_URL" CHAT_ID="$CHAT_ID" LIMIT=20 \
    "${SCRIPT_DIR}/smoke-admin-export-global-jobs.sh"
fi

if ! $SKIP_OBSERVABILITY; then
  step "prometheus export metrics" \
    "${SCRIPT_DIR}/smoke-export-observability.sh"
fi

if ! $SKIP_DOWNLOAD; then
  step "compliance flow + bundle download (with file)" \
    "${SCRIPT_DIR}/smoke-export-compliance-flow.sh" --chat-id "$CHAT_ID" --url "$BASE_URL" --skip-prep --include-file
fi

if ! $SKIP_METRICS; then
  step "worker metrics" \
    "${SCRIPT_DIR}/smoke-export-worker-metrics.sh" --chat-id "$CHAT_ID" --url "$BASE_URL" \
    --worker-url "$WORKER_METRICS_URL" "${AUDIT_FLAG[@]}"
fi

echo "" >&2
echo "[OK] export compliance pack finished (chat $CHAT_ID)" >&2
