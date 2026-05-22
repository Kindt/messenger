#!/usr/bin/env bash
# User API: POST /v1/chats/{id}/export -> DELETE cancel -> export_cancelled.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
MODE="${MODE:-any}"
POLL_SECONDS="${POLL_SECONDS:-120}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-1}"
REQUIRE_PROCESSING=false
SMOKE_SKIP_AUDIT="${SMOKE_SKIP_AUDIT:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-export-audit.sh
source "${SCRIPT_DIR}/lib/smoke-export-audit.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --mode|-m) MODE="$2"; shift 2 ;;
    --poll-seconds) POLL_SECONDS="$2"; shift 2 ;;
    --require-processing) REQUIRE_PROCESSING=true; shift ;;
    --skip-audit) SMOKE_SKIP_AUDIT=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--mode queued|processing|any] [--require-processing] [--skip-audit]"
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

export_uri="${BASE_URL}/api/v1/chats/${CHAT_ID}/export"
echo "POST user export (mode=$MODE) ..." >&2
accept=$(curl -sS -w "\n%{http_code}" -X POST "$export_uri" "${AUTH[@]}" -H "Content-Type: application/json" -d "{}") || fail "POST"
code=$(echo "$accept" | tail -n1)
[[ "$code" == "202" ]] || fail "expected 202 (403 = not owner/admin?)"
body=$(echo "$accept" | sed '$d')
job_id=$(echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('job_id') or d.get('jobId') or '')")
[[ -n "$job_id" ]] || fail "no job_id"
echo "[OK] job_id=$job_id" >&2

status_uri="${export_uri}/${job_id}"
deadline=$(( $(date +%s) + POLL_SECONDS ))
cancelled=false
saw_processing=false

while [[ $(date +%s) -lt $deadline ]]; do
  st=$(curl -fsS "$status_uri" "${AUTH[@]}") || fail "status"
  status=$(echo "$st" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))")
  echo "  status=$status" >&2
  [[ "$status" == "processing" ]] && saw_processing=true
  if should_cancel "$status"; then
    echo "DELETE cancel ..." >&2
    curl -fsS -X DELETE "$status_uri" "${AUTH[@]}" >/dev/null || fail "cancel"
    cancelled=true
    break
  fi
  if [[ "$status" == "export_cancelled" ]]; then
    cancelled=true
    break
  fi
  if [[ "$status" == "export_v1" || "$status" == "stub_written" ]]; then
    if [[ "$MODE" == "processing" && "$REQUIRE_PROCESSING" == true ]]; then
      fail "finished before processing"
    fi
    echo "[WARN] job finished before cancel" >&2
    exit 0
  fi
  sleep "$POLL_INTERVAL_SEC"
done

if ! $cancelled; then
  curl -fsS -X DELETE "$status_uri" "${AUTH[@]}" >/dev/null || fail "cancel"
fi

if [[ "$MODE" == "processing" && "$REQUIRE_PROCESSING" == true && "$saw_processing" != true ]]; then
  fail "never observed processing"
fi

final=$(curl -fsS "$status_uri" "${AUTH[@]}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))")
[[ "$final" == "export_cancelled" ]] || fail "expected export_cancelled, got $final"
echo "[OK] export_cancelled (user API)" >&2

verify_export_cancel_audit "$BASE_URL" "$TOKEN" "$job_id" "export.cancelled" || exit 1
