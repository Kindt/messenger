#!/usr/bin/env bash
# E2E: msg.export.suggested -> EXPORT_AUTO_QUEUE_ON_SUGGESTED -> export.auto_queued.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
NATS_URL="${NATS_URL:-}"
POLL_SECONDS="${POLL_SECONDS:-30}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --nats-url) NATS_URL="$2"; shift 2 ;;
    -h|--help) echo "Usage: $0 --chat-id UUID"; exit 0 ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

pub_args=(--chat-id "$CHAT_ID")
[[ -n "$NATS_URL" ]] && pub_args+=(--nats-url "$NATS_URL")
"${SCRIPT_DIR}/publish-export-suggested.sh" "${pub_args[@]}"

token="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"csadmin","password":"csadmin"}' \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("access_token") or d.get("accessToken",""))')"
[[ -n "$token" ]] || fail "No token"

deadline=$((SECONDS + POLL_SECONDS))
found=false
while (( SECONDS < deadline )); do
  body="$(curl -sf "${BASE_URL}/api/v1/admin/audit-events?limit=20&action=export.auto_queued&resource_type=export_job" \
    -H "Authorization: Bearer $token")"
  if echo "$body" | python3 -c "import json,sys; cid=sys.argv[1]; rows=json.load(sys.stdin); print(any(cid in (r.get('details_json') or '') for r in (rows if isinstance(rows,list) else [])))" "$CHAT_ID" | grep -q True; then
    found=true
    break
  fi
  sleep 1
done

$found || fail "No export.auto_queued for chat (EXPORT_AUTO_QUEUE_ON_SUGGESTED=true?)"

jobs="$(curl -sf "${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export/jobs?limit=5&status=queued" \
  -H "Authorization: Bearer $token")"
echo "$jobs" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("[OK] queued jobs:", d.get("job_count",0))' >&2
echo "[OK] NATS export.suggested -> auto-queue" >&2
