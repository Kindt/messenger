#!/usr/bin/env bash
# export-suggest (local) -> export job -> cancel -> audits.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
MODE="${CANCEL_MODE:-any}"
POLL_SECONDS="${POLL_SECONDS:-120}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-1}"
SMOKE_SKIP_AUDIT="${SMOKE_SKIP_AUDIT:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-export-audit.sh
source "${SCRIPT_DIR}/lib/smoke-export-audit.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --mode|-m) MODE="$2"; shift 2 ;;
    --skip-audit) SMOKE_SKIP_AUDIT=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--mode queued|processing|any]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

should_cancel() {
  local status="$1"
  case "$MODE" in
    queued) [[ "$status" == "queued" ]] ;;
    processing) [[ "$status" == "processing" ]] ;;
    *) [[ "$status" == "queued" || "$status" == "processing" ]] ;;
  esac
}

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

TOKEN=$(token)
[[ -n "$TOKEN" ]] || fail "login failed"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

echo "POST export-suggest (local) ..." >&2
suggest=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export-suggest" \
  "${AUTH[@]}" -H "Content-Type: application/json" \
  -d '{"dispatch":"local","candidate_message_count":2,"reason":"hot_body_candidates"}') || fail "suggest"
code=$(echo "$suggest" | tail -n1)
body=$(echo "$suggest" | sed '$d')
[[ "$code" == "202" ]] || fail "export-suggest HTTP $code"
echo "[OK] export-suggest" >&2

audit=$(curl -fsS "${BASE_URL}/api/v1/admin/audit-events?action=export.suggested&resource_id=${CHAT_ID}&limit=5" \
  "${AUTH[@]}") || fail "audit suggest"
count=$(echo "$audit" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else 0)")
[[ "$count" -gt 0 ]] || fail "no export.suggested audit"
echo "[OK] audit export.suggested" >&2

job_id=$(echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('auto_queued_job_id') or d.get('autoQueuedJobId') or '')")
if [[ -z "$job_id" ]]; then
  echo "No auto_queued_job_id — POST admin export ..." >&2
  accept=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export" \
    "${AUTH[@]}" -H "Content-Type: application/json" -d "{}") || fail "export POST"
  acode=$(echo "$accept" | tail -n1)
  [[ "$acode" == "202" ]] || fail "admin export expected 202"
  abody=$(echo "$accept" | sed '$d')
  job_id=$(echo "$abody" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('job_id') or d.get('jobId') or '')")
fi
[[ -n "$job_id" ]] || fail "no job_id"
echo "[OK] job_id=$job_id" >&2

verify_export_requested_audit "$BASE_URL" "$TOKEN" "$job_id" || exit 1

status_uri="${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export/${job_id}/status"
cancel_uri="${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export/${job_id}"
deadline=$(( $(date +%s) + POLL_SECONDS ))
cancelled=false

while [[ $(date +%s) -lt $deadline ]]; do
  st=$(curl -fsS "$status_uri" "${AUTH[@]}") || fail "status"
  status=$(echo "$st" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))")
  echo "  status=$status" >&2
  if should_cancel "$status"; then
    curl -fsS -X DELETE "$cancel_uri" "${AUTH[@]}" >/dev/null || fail "cancel"
    cancelled=true
    break
  fi
  if [[ "$status" == "export_cancelled" ]]; then
    cancelled=true
    break
  fi
  if [[ "$status" == "export_v1" || "$status" == "stub_written" ]]; then
    echo "[WARN] finished before cancel" >&2
    exit 0
  fi
  sleep "$POLL_INTERVAL_SEC"
done

if ! $cancelled; then
  curl -fsS -X DELETE "$cancel_uri" "${AUTH[@]}" >/dev/null || fail "cancel"
fi

final=$(curl -fsS "$status_uri" "${AUTH[@]}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))")
[[ "$final" == "export_cancelled" ]] || fail "expected export_cancelled, got $final"

verify_export_cancel_audit "$BASE_URL" "$TOKEN" "$job_id" "export.admin_cancelled" || exit 1
echo "[OK] suggest -> export -> cancel complete" >&2
