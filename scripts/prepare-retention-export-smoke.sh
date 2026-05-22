#!/usr/bin/env bash
# PATCH chat retention so hot-body janitor can select candidates (smoke / dev).
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-csadmin}"
ADMIN_PASS="${ADMIN_PASS:-csadmin}"
HOT_BODY_DAYS="${HOT_BODY_DAYS:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --hot-body-days) HOT_BODY_DAYS="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--hot-body-days N]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

token="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("access_token") or d.get("accessToken",""))')"
[[ -n "$token" ]] || fail "No admin token"

body="$(python3 -c "import json; print(json.dumps({
  'hot_message_body_max_age_days': int('$HOT_BODY_DAYS'),
  'hot_metadata_min_age_days': None,
  'archive_metadata_enabled': False,
  'deep_archive_enabled': True,
  'legal_hold': False,
}))")"

uri="$BASE_URL/api/v1/admin/chats/$CHAT_ID/retention"
echo "PATCH $uri (hot_body=$HOT_BODY_DAYS) ..." >&2
curl -sf -X PATCH "$uri" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" >/dev/null
echo "[OK] chat retention prepared" >&2
