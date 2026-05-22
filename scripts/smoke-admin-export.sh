#!/usr/bin/env bash
# Admin POST export -> poll -> download. Requires EXPORT_ADMIN_EXPORT_ENABLED=true.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
POLL_SECONDS="${POLL_SECONDS:-120}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-2}"
SKIP_DOWNLOAD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --poll-seconds) POLL_SECONDS="$2"; shift 2 ;;
    --skip-download) SKIP_DOWNLOAD=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--url URL] [--poll-seconds N] [--skip-download]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

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

echo "POST admin export chat=$CHAT_ID ..." >&2
accept=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export" \
  "${AUTH[@]}" -H "Content-Type: application/json" -d "{}") || fail "POST export"
code=$(echo "$accept" | tail -n1)
body=$(echo "$accept" | sed '$d')
[[ "$code" == "202" ]] || fail "expected 202, got $code: $body"
job_id=$(echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('job_id') or d.get('jobId') or '')")
[[ -n "$job_id" ]] || fail "no job_id"
echo "[OK] job_id=$job_id" >&2

status_uri="${BASE_URL}/api/v1/admin/chats/${CHAT_ID}/export/${job_id}/status"
deadline=$(( $(date +%s) + POLL_SECONDS ))
terminal_re='^(export_v1|stub_written|export_failed|export_cancelled)$'

while [[ $(date +%s) -lt $deadline ]]; do
  sleep "$POLL_INTERVAL_SEC"
  st=$(curl -fsS "$status_uri" "${AUTH[@]}") || fail "poll"
  status=$(echo "$st" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status',''))")
  echo "  status=$status" >&2
  if [[ "$status" =~ $terminal_re ]]; then
    echo "[OK] finished: $status" >&2
    if ! $SKIP_DOWNLOAD; then
      dl="${status_uri%/status}/download?part=bundle"
      echo "GET download bundle ..." >&2
      if curl -fsS -o /tmp/korus_admin_export.bin "$dl" "${AUTH[@]}"; then
        bytes=$(wc -c < /tmp/korus_admin_export.bin | tr -d ' ')
        echo "[OK] download: $bytes bytes" >&2
      fi
    fi
    exit 0
  fi
done

fail "timed out waiting for job $job_id"
