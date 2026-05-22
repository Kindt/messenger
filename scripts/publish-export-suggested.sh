#!/usr/bin/env bash
# Publish msg.export.suggested to NATS (dev/smoke). Requires nats CLI and NATS_URL.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
NATS_URL="${NATS_URL:-nats://127.0.0.1:4222}"
CANDIDATE_COUNT="${CANDIDATE_COUNT:-3}"
REASON="${REASON:-hot_body_candidates}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --nats-url) NATS_URL="$2"; shift 2 ;;
    --candidates) CANDIDATE_COUNT="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--nats-url URL] [--candidates N]"
      exit 0
      ;;
    *) fail "Unknown option: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v nats >/dev/null 2>&1 || fail "nats CLI not in PATH"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

payload=$(python3 -c "
import json, time
print(json.dumps({
  'chatId': '$CHAT_ID',
  'reason': '$REASON',
  'candidateMessageCount': int('$CANDIDATE_COUNT'),
  'suggestedAtEpochMs': int(time.time() * 1000),
}, separators=(',', ':')))
")

echo "Publishing msg.export.suggested chatId=$CHAT_ID ..." >&2
nats --server "$NATS_URL" pub msg.export.suggested "$payload"
echo "[OK] Published. Run ./scripts/smoke-export-suggested.sh to verify audit." >&2
