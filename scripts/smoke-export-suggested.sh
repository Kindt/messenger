#!/usr/bin/env bash
# Smoke: export.suggested / export.auto_queued audit rows (after retention batch).
# Usage: ./scripts/smoke-export-suggested.sh [--url URL] [--chat-id UUID]
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
CHAT_ID=""
LIMIT="${LIMIT:-20}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --user) KORUS_USER="$2"; shift 2 ;;
    --pass) KORUS_PASS="$2"; shift 2 ;;
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--url URL] [--chat-id UUID]"
      echo "Requires python3. Run retention with RETENTION_PUBLISH_EXPORT_SUGGESTED=true first."
      exit 0
      ;;
    *) fail "Unknown option: $1" ;;
  esac
done

command -v python3 >/dev/null 2>&1 || fail "python3 required"

json_get() {
  python3 -c '
import json, sys
key = sys.argv[1]
data = json.load(sys.stdin)
parts = key.split("_")
camel = parts[0] + "".join(p.capitalize() for p in parts[1:])
for n in (key, camel):
    if n in data and data[n] is not None:
        print(data[n])
        break
else:
    print("")
' "$1"
}

login_resp=$(curl -fsS -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "$(printf '{"username":"%s","password":"%s"}' "$KORUS_USER" "$KORUS_PASS")") || fail "login"
token=$(echo "$login_resp" | json_get access_token)
[[ -n "$token" ]] || fail "no access token"

fetch_audit() {
  local action="$1"
  local qs="limit=${LIMIT}&action=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$action'))")"
  if [[ -n "$CHAT_ID" ]]; then
    qs="${qs}&resource_type=chat&resource_id=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$CHAT_ID'))")"
  fi
  curl -fsS "${BASE_URL}/api/v1/admin/audit-events?${qs}" \
    -H "Authorization: Bearer $token"
}

any=false
for action in export.suggested export.auto_queued export.auto_queue_skipped; do
  body=$(fetch_audit "$action") || fail "audit $action"
  count=$(echo "$body" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else 0)")
  echo "  $action : $count row(s)" >&2
  if [[ "$count" -gt 0 ]]; then any=true; fi
done

if ! $any; then
  echo "[WARN] No export suggestion audit rows. Set RETENTION_PUBLISH_EXPORT_SUGGESTED=true on retention worker." >&2
  exit 1
fi
echo "[OK] export suggestion audit trail present" >&2
