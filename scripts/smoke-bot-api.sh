#!/usr/bin/env bash
# Bot API REST smoke (spec 009 T203): register, subscribe, sendMessage.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
USER="${SMOKE_USER:-csadmin}"
PASS="${SMOKE_PASS:-csadmin}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--url BASE]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

SUFFIX="$(date +%Y%m%d%H%M%S)"
BOT_NAME="smoke_bot_${SUFFIX}"
WEBHOOK="https://example.com/korus-bot-smoke/${SUFFIX}"
MSG="bot-smoke-${SUFFIX}"

smoke_step "Login"
TOKEN=$(smoke_login "$BASE_URL" "$USER" "$PASS")

smoke_step "Create bot"
CREATE_BODY=$(python3 -c 'import json,sys; print(json.dumps({"bot_name":sys.argv[1],"display_name":"Smoke Bot","listen_mode":"READ_ALL","default_webhook_url":sys.argv[2]}))' "$BOT_NAME" "$WEBHOOK")
CREATE_RESP=$(curl -sS -w '\n%{http_code}' -X POST "${BASE_URL%/}/api/v1/bots" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "$CREATE_BODY") || smoke_fail "create bot curl failed"
CREATE_CODE=$(echo "$CREATE_RESP" | tail -n1)
CREATE_JSON=$(echo "$CREATE_RESP" | sed '$d')
if [[ "$CREATE_CODE" != "201" ]]; then
  echo "POST /bots HTTP $CREATE_CODE: $CREATE_JSON" >&2
  smoke_fail "create bot failed (V032 migrated?)"
fi
BOT_ID=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d["bot_id"])' "$CREATE_JSON")
BOT_TOKEN=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); t=d.get("access_token"); (sys.exit(1) if not t or not t.startswith("kbt_") else None); print(t)' "$CREATE_JSON") \
  || smoke_fail "access_token missing"

smoke_step "Create group chat"
GROUP_TITLE="bot-smoke-${SUFFIX}"
CHAT_ID=$(smoke_create_group "$BASE_URL" "$TOKEN" "$GROUP_TITLE" "")

smoke_step "Subscribe bot to chat"
SUB_CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  "${BASE_URL%/}/api/v1/bots/${BOT_ID}/chats/${CHAT_ID}/subscribe" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d '{}')
if [[ "$SUB_CODE" != "201" ]]; then
  smoke_fail "subscribe HTTP $SUB_CODE"
fi

smoke_step "Send message as bot"
SEND_BODY=$(python3 -c 'import json,sys; print(json.dumps({"chat_id":sys.argv[1],"type":"text","content":sys.argv[2],"client_msg_id":"bot-smoke"}))' "$CHAT_ID" "$MSG")
SEND_RESP=$(curl -sS -w '\n%{http_code}' -X POST "${BASE_URL%/}/api/v1/bot/send" \
  -H "Authorization: Bearer $BOT_TOKEN" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "$SEND_BODY") || smoke_fail "bot send curl failed"
SEND_CODE=$(echo "$SEND_RESP" | tail -n1)
SEND_JSON=$(echo "$SEND_RESP" | sed '$d')
if [[ "$SEND_CODE" != "201" ]]; then
  smoke_fail "bot send HTTP $SEND_CODE"
fi
MSG_ID=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("id") or d.get("message_id") or "")' "$SEND_JSON")
if [[ -z "$MSG_ID" ]]; then
  smoke_fail "bot send missing message id"
fi

smoke_step "Verify bot message in chat history"
found=0
for i in $(seq 1 15); do
  LIST=$(smoke_curl_json GET "${BASE_URL%/}/api/v1/chats/${CHAT_ID}/messages?limit=20" "$TOKEN") || smoke_fail "list messages failed"
  if python3 -c 'import json,sys; mid=sys.argv[2]; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); sys.exit(0 if any((m.get("id") or m.get("message_id"))==mid for m in items) else 1)' "$LIST" "$MSG_ID"; then
    found=1
    break
  fi
  sleep 1
done
if [[ "$found" -ne 1 ]]; then
  smoke_fail "bot message id not in history"
fi

echo "[OK] smoke-bot-api ($BOT_NAME -> chat $CHAT_ID)" >&2
