#!/usr/bin/env bash
# Spec 002 API parity smoke (bash subset T010). Canonical on Linux/CI.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
USER="${SMOKE_USER:-smoke_user_a}"
PASS="${SMOKE_PASS:-smokepass123}"
SECOND_USER="${SMOKE_SECOND_USER:-smoke_user_b}"
SECOND_PASS="${SMOKE_SECOND_PASS:-smokepass123}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    -h|--help) echo "Usage: $0 [--url URL]"; exit 0 ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

TOKEN=$(smoke_login "$BASE_URL" "$USER" "$PASS")
TOKEN2=$(smoke_login "$BASE_URL" "$SECOND_USER" "$SECOND_PASS")
ID2=$(smoke_user_id "$BASE_URL" "$TOKEN2")

smoke_step "create group chat"
TITLE="parity-api-$(date +%Y%m%d-%H%M%S)"
CHAT=$(smoke_create_group "$BASE_URL" "$TOKEN" "$TITLE" "$ID2")

smoke_step "send and edit message"
MSG=$(smoke_send_message "$BASE_URL" "$TOKEN" "$CHAT" "parity-send")
smoke_send_message "$BASE_URL" "$TOKEN" "$CHAT" "parity-reply" "$MSG" >/dev/null

body=$(python3 -c 'import json; print(json.dumps({"content":"parity-edited"}))')
edited=$(smoke_curl_json PATCH "${BASE_URL%/}/api/v1/chats/${CHAT}/messages/${MSG}" "$TOKEN" "$body") || smoke_fail "edit"
echo "$edited" | grep -q parity-edited || smoke_fail "edit content"

smoke_step "reaction add/list"
body='{"reaction":"thumbsup"}'
smoke_curl_json POST "${BASE_URL%/}/api/v1/chats/${CHAT}/messages/${MSG}/reactions" "$TOKEN" "$body" >/dev/null
smoke_curl_json GET "${BASE_URL%/}/api/v1/chats/${CHAT}/messages/${MSG}/reactions" "$TOKEN" >/dev/null

smoke_step "pin message"
smoke_curl_json POST "${BASE_URL%/}/api/v1/chats/${CHAT}/messages/${MSG}/pin" "$TOKEN" >/dev/null
pins=$(smoke_curl_json GET "${BASE_URL%/}/api/v1/chats/${CHAT}/messages/pins" "$TOKEN") || smoke_fail "pins"
[[ -n "$pins" && "$pins" != "[]" ]] || smoke_fail "pins empty"

smoke_step "standalone conference (Telemost)"
body='{"title":"parity-meet"}'
CONF=$(smoke_curl_json POST "${BASE_URL%/}/api/v1/conferences" "$TOKEN" "$body") || smoke_fail "create conference"
echo "$CONF" | grep -q '"join_url"' || smoke_fail "join_url missing"
CONF_ID=$(echo "$CONF" | python3 -c 'import json,sys; print(json.load(sys.stdin)["conference_id"])')
ROOM=$(echo "$CONF" | python3 -c 'import json,sys; print(json.load(sys.stdin)["room_slug"])')
smoke_curl_json POST "${BASE_URL%/}/api/v1/conferences/${CONF_ID}/join" "$TOKEN" >/dev/null || smoke_fail "join"
smoke_curl_json GET "${BASE_URL%/}/api/v1/conferences/by-room/${ROOM}" "$TOKEN" >/dev/null || smoke_fail "by-room"
smoke_curl_json POST "${BASE_URL%/}/api/v1/conferences/${CONF_ID}/leave" "$TOKEN" >/dev/null || smoke_fail "leave"
smoke_curl_json POST "${BASE_URL%/}/api/v1/conferences/${CONF_ID}/end" "$TOKEN" >/dev/null || smoke_fail "end"

echo ""
echo "[OK] smoke-web-parity-api (spec 002 T010 subset)"
