#!/usr/bin/env bash
# Seed non-empty chat messages for retention hot-body candidate SELECT.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
USER="${KORUS_USER:-csadmin}"
PASS="${KORUS_PASS:-csadmin}"
MESSAGE_COUNT="${MESSAGE_COUNT:-3}"
CREATE_GROUP=false
PREPARE=false
INCLUDE_FILE=false
FILE_NAME="compliance-smoke.txt"
AGE_BUFFER_SEC="${AGE_BUFFER_SEC:-2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --count) MESSAGE_COUNT="$2"; shift 2 ;;
    --create-group) CREATE_GROUP=true; shift ;;
    --prepare) PREPARE=true; shift ;;
    --include-file) INCLUDE_FILE=true; shift ;;
    --file-name) FILE_NAME="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--create-group] [--prepare] [--include-file] [--count N]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

command -v python3 >/dev/null 2>&1 || fail "python3 required"

json_get() {
  python3 -c 'import json,sys; k=sys.argv[1]; d=json.load(sys.stdin); c=k.split("_"); camel=c[0]+"".join(x.capitalize() for x in c[1:]);
for n in (k,camel):
  if n in d and d[n] is not None: print(d[n]); break' "$1"
}

token="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(printf '{"username":"%s","password":"%s"}' "$USER" "$PASS")" \
  | json_get access_token)"
[[ -n "$token" ]] || fail "No token"

auth=(-H "Authorization: Bearer $token" -H 'Content-Type: application/json')

if $PREPARE; then
  if [[ -z "$CHAT_ID" ]] && ! $CREATE_GROUP; then
    CHAT_ID="$(curl -sf "$BASE_URL/api/v1/chats" -H "Authorization: Bearer $token" \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d[0].get("id") or d[0].get("chat_id") or "") if d else "")')"
    [[ -n "$CHAT_ID" ]] || fail "No chats; use --create-group"
    echo "Using chat $CHAT_ID" >&2
  fi
  prep_body="$(python3 -c "
import json, os
cid=os.environ.get('CHAT_ID','')
cg=os.environ.get('CREATE_GROUP','0')=='1' and not cid
body={'message_count': int(os.environ.get('MESSAGE_COUNT','3')), 'create_group': cg}
if cid: body['chat_id']=cid
if os.environ.get('INCLUDE_FILE','0')=='1':
    body['include_file']=True
    body['file_name']=os.environ.get('FILE_NAME','compliance-smoke.txt')
print(json.dumps(body))
" CHAT_ID="$CHAT_ID" CREATE_GROUP="$($CREATE_GROUP && echo 1 || echo 0)" MESSAGE_COUNT="$MESSAGE_COUNT" INCLUDE_FILE="$($INCLUDE_FILE && echo 1 || echo 0)" FILE_NAME="$FILE_NAME")"
  echo "POST export-compliance-prep ..." >&2
  prep="$(curl -sf -X POST "$BASE_URL/api/v1/admin/export-compliance-prep" "${auth[@]}" -d "$prep_body")"
  CHAT_ID="$(printf '%s' "$prep" | json_get chat_id)"
  [[ -n "$CHAT_ID" ]] || CHAT_ID="$(printf '%s' "$prep" | json_get chatId)"
  [[ -n "$CHAT_ID" ]] || fail "prep did not return chat_id"
  echo "[OK] prep chat=$CHAT_ID" >&2
else
  if [[ -z "$CHAT_ID" ]]; then
    if $CREATE_GROUP; then
      echo "POST group chat ..." >&2
      CHAT_ID="$(curl -sf -X POST "$BASE_URL/api/v1/chats" "${auth[@]}" \
        -d '{"type":"group","title":"retention-smoke","member_ids":[]}' | json_get id)"
      [[ -n "$CHAT_ID" ]] || fail "create chat"
      echo "[OK] created chat $CHAT_ID" >&2
    else
      CHAT_ID="$(curl -sf "$BASE_URL/api/v1/chats" -H "Authorization: Bearer $token" \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d[0].get("id") or d[0].get("chat_id") or "") if d else "")')"
      [[ -n "$CHAT_ID" ]] || fail "No chats; use --create-group"
      echo "Using chat $CHAT_ID" >&2
    fi
  fi

  for ((i=1; i<=MESSAGE_COUNT; i++)); do
    body="$(python3 -c "import json,datetime; print(json.dumps({'type':'text','content':'retention-smoke seed $i '+datetime.datetime.now(datetime.timezone.utc).isoformat()}))")"
    mid="$(curl -sf -X POST "$BASE_URL/api/v1/chats/$CHAT_ID/messages" "${auth[@]}" -d "$body" | json_get id)"
    echo "  message $i id=$mid" >&2
  done
fi

if [[ "$AGE_BUFFER_SEC" -gt 0 ]]; then
  echo "Waiting ${AGE_BUFFER_SEC}s ..." >&2
  sleep "$AGE_BUFFER_SEC"
fi

echo "[OK] seeded $MESSAGE_COUNT message(s) in chat $CHAT_ID" >&2
echo "$CHAT_ID"
