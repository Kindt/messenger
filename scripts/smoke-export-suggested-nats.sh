#!/usr/bin/env bash
# E2E: publish msg.export.suggested via NATS CLI, verify core-api audit export.suggested.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
NATS_URL="${NATS_URL:-}"
POLL_SECONDS="${POLL_SECONDS:-30}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --nats-url) NATS_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--url URL] [--nats-url URL]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"

pub_args=(--chat-id "$CHAT_ID")
[[ -n "$NATS_URL" ]] && pub_args+=(--nats-url "$NATS_URL")
"${SCRIPT_DIR}/publish-export-suggested.sh" "${pub_args[@]}"

deadline=$((SECONDS + POLL_SECONDS))
while (( SECONDS < deadline )); do
  if BASE_URL="$BASE_URL" CHAT_ID="$CHAT_ID" LIMIT=5 "${SCRIPT_DIR}/smoke-export-suggested.sh" 2>/dev/null; then
    echo "[OK] NATS export.suggested -> audit" >&2
    exit 0
  fi
  sleep "$POLL_INTERVAL_SEC"
done

fail "Timed out waiting for export.suggested audit (chat $CHAT_ID)"
