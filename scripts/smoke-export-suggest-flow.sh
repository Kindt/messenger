#!/usr/bin/env bash
# Admin export-suggest -> audit -> optional poll/download (smoke-export-chat.sh).
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
CHAT_USER="${CHAT_USER:-csadmin}"
CHAT_PASS="${CHAT_PASS:-csadmin}"
DISPATCH="${DISPATCH:-local}"
POLL_SECONDS="${POLL_SECONDS:-120}"
SKIP_EXPORT_POLL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --dispatch) DISPATCH="$2"; shift 2 ;;
    --chat-user) CHAT_USER="$2"; shift 2 ;;
    --chat-pass) CHAT_PASS="$2"; shift 2 ;;
    --poll-seconds) POLL_SECONDS="$2"; shift 2 ;;
    --skip-export-poll) SKIP_EXPORT_POLL=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--url URL] [--dispatch local|nats|both] [--chat-user U] [--poll-seconds N]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

token() {
  python3 -c '
import json, sys, urllib.request
base, user, pw = sys.argv[1], sys.argv[2], sys.argv[3]
req = urllib.request.Request(
  base + "/api/v1/auth/login",
  data=json.dumps({"username": user, "password": pw}).encode(),
  headers={"Content-Type": "application/json"},
  method="POST",
)
with urllib.request.urlopen(req) as r:
  d = json.load(r)
print(d.get("access_token") or d.get("accessToken") or "")
' "$BASE_URL" "$KORUS_USER" "$KORUS_PASS"
}

ADMIN_TOKEN=$(token)
[[ -n "$ADMIN_TOKEN" ]] || fail "admin login failed"

echo "POST export-suggest dispatch=$DISPATCH ..." >&2
suggest=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export-suggest" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$(python3 -c "import json; print(json.dumps({'dispatch':'$DISPATCH','candidate_message_count':2,'reason':'hot_body_candidates'}))")")
code=$(echo "$suggest" | tail -n1)
body=$(echo "$suggest" | sed '$d')
[[ "$code" == "202" ]] || fail "export-suggest HTTP $code: $body"
echo "[OK] export-suggest" >&2
echo "$body" >&2

audit=$(curl -fsS "${BASE_URL}/api/v1/admin/audit-events?action=export.suggested&resource_id=${CHAT_ID}&limit=5" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}") || fail "audit"
count=$(echo "$audit" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else 0)")
[[ "$count" -gt 0 ]] || fail "no export.suggested audit"
echo "[OK] audit export.suggested: $count row(s)" >&2

if $SKIP_EXPORT_POLL; then
  exit 0
fi

job_id=$(echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('auto_queued_job_id') or d.get('autoQueuedJobId') or '')")
if [[ -n "$job_id" ]]; then
  echo "Polling auto-queued job $job_id as $CHAT_USER ..." >&2
  exec "${SCRIPT_DIR}/smoke-export-chat.sh" --url "$BASE_URL" --chat-id "$CHAT_ID" \
    --job-id "$job_id" --user "$CHAT_USER" --pass "$CHAT_PASS" --poll-seconds "$POLL_SECONDS"
fi

echo "No auto_queued_job_id (EXPORT_AUTO_QUEUE_ON_SUGGESTED?). Run smoke-export-chat.sh manually." >&2
exit 0
