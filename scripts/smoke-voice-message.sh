#!/usr/bin/env bash
# Spec 022: voice message roundtrip smoke (upload + type=voice).
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
API="${BASE_URL}/api/v1"
USER="${SMOKE_USER:-smoke_user_a}"
PASS="${SMOKE_PASS:-smoke}"

token=$(curl -sf -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq -r '.access_token')
test -n "$token"

chat_id=$(curl -sf "$API/chats" -H "Authorization: Bearer $token" | jq -r '.[0].id')
test -n "$chat_id"

tmp=$(mktemp)
printf 'voice-smoke' >"$tmp"
up=$(curl -sf -X POST "$API/files/upload" -H "Authorization: Bearer $token" -F "file=@$tmp;type=audio/webm")
file_id=$(echo "$up" | jq -r '.id')
rm -f "$tmp"
test -n "$file_id"

msg=$(curl -sf -X POST "$API/chats/$chat_id/messages" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' \
  -d "{\"type\":\"voice\",\"content\":\"$file_id\",\"duration_ms\":1200}")
echo "$msg" | jq -e '.type == "voice"' >/dev/null
echo "[OK] voice message smoke chat=$chat_id file=$file_id"
