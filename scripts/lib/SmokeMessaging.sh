#!/usr/bin/env bash
# Shared REST/JSON helpers for messaging smokes (spec 003).

smoke_fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

smoke_step() {
  echo ""
  echo "== $1 ==" >&2
}

smoke_curl_json() {
  local method="$1" url="$2" token="$3" body="${4:-}"
  local tmp code
  tmp=$(mktemp)
  if [[ -n "$body" ]]; then
    code=$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json; charset=utf-8" \
      -d "$body")
  else
    code=$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token")
  fi
  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "HTTP $code for $method $url: $(cat "$tmp")" >&2
    rm -f "$tmp"
    return 1
  fi
  cat "$tmp"
  rm -f "$tmp"
}

smoke_login() {
  local base="$1" user="$2" pass="$3"
  local resp
  resp=$(curl -sS -X POST "${base%/}/api/v1/auth/login" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"username\":\"$user\",\"password\":\"$pass\"}") || smoke_fail "login curl failed for $user"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); t=d.get("access_token") or d.get("accessToken"); (sys.exit(1) if not t else None); print(t)' "$resp" \
    || smoke_fail "login parse failed for $user"
}

smoke_register() {
  local base="$1" user="$2" pass="$3" display="$4"
  local code resp
  resp=$(mktemp)
  code=$(curl -sS -o "$resp" -w '%{http_code}' -X POST "${base%/}/api/v1/auth/register" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"username\":\"$user\",\"password\":\"$pass\",\"display_name\":\"$display\"}")
  if [[ "$code" == "201" || "$code" == "200" || "$code" == "409" ]]; then
    rm -f "$resp"
    return 0
  fi
  echo "register $user HTTP $code: $(cat "$resp")" >&2
  rm -f "$resp"
  return 1
}

smoke_user_id() {
  local base="$1" token="$2"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/users/me" "$token") || smoke_fail "users/me failed"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); uid=d.get("id") or d.get("user_id"); (sys.exit(1) if not uid else None); print(uid)' "$resp" \
    || smoke_fail "users/me id missing"
}

smoke_create_p2p() {
  local base="$1" token="$2" member_id="$3"
  local body resp
  body=$(python3 -c 'import json,sys; print(json.dumps({"type":"p2p","member_ids":[sys.argv[1]]}))' "$member_id")
  resp=$(smoke_curl_json POST "${base%/}/api/v1/chats" "$token" "$body") || smoke_fail "create p2p failed"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); cid=d.get("id") or d.get("chat_id"); (sys.exit(1) if not cid else None); print(cid)' "$resp" \
    || smoke_fail "p2p chat id missing"
}

smoke_create_group() {
  local base="$1" token="$2" title="$3" members_csv="$4"
  local body resp
  body=$(python3 -c 'import json,sys; print(json.dumps({"type":"group","title":sys.argv[1],"member_ids":[x for x in sys.argv[2].split(",") if x]}))' "$title" "$members_csv")
  resp=$(smoke_curl_json POST "${base%/}/api/v1/chats" "$token" "$body") || smoke_fail "create group failed"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); cid=d.get("id") or d.get("chat_id"); (sys.exit(1) if not cid else None); print(cid)' "$resp" \
    || smoke_fail "group chat id missing"
}

smoke_send_message() {
  local base="$1" token="$2" chat_id="$3" content="$4" reply_to="${5:-}"
  local body resp
  if [[ -n "$reply_to" ]]; then
    body=$(python3 -c 'import json,sys; print(json.dumps({"type":"text","content":sys.argv[1],"reply_to_msg_id":sys.argv[2]}))' "$content" "$reply_to")
  else
    body=$(python3 -c 'import json,sys; print(json.dumps({"type":"text","content":sys.argv[1]}))' "$content")
  fi
  resp=$(smoke_curl_json POST "${base%/}/api/v1/chats/${chat_id}/messages" "$token" "$body") || smoke_fail "send message failed"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); mid=d.get("id") or d.get("message_id"); (sys.exit(1) if not mid else None); print(mid)' "$resp" \
    || smoke_fail "message id missing"
}

smoke_message_count() {
  local base="$1" token="$2" chat_id="$3"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/messages?limit=50" "$token") || smoke_fail "list messages failed"
  python3 -c 'import json,sys; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); print(len(items))' "$resp"
}

smoke_messages_contain_id() {
  local base="$1" token="$2" chat_id="$3" message_id="$4"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/messages?limit=50" "$token") || smoke_fail "list messages failed"
  python3 -c 'import json,sys; mid=sys.argv[2]; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); sys.exit(0 if any((m.get("id") or m.get("message_id"))==mid for m in items) else 1)' "$resp" "$message_id"
}

smoke_poll_message_id() {
  local base="$1" token="$2" chat_id="$3" message_id="$4" timeout="${5:-15}"
  local i
  for ((i=0; i<timeout; i++)); do
    if smoke_messages_contain_id "$base" "$token" "$chat_id" "$message_id"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

smoke_poll_min_message_count() {
  local base="$1" token="$2" chat_id="$3" min_count="$4" timeout="${5:-15}"
  local i count
  for ((i=0; i<timeout; i++)); do
    count=$(smoke_message_count "$base" "$token" "$chat_id")
    if [[ "$count" -ge "$min_count" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

smoke_messages_contain() {
  local base="$1" token="$2" chat_id="$3" needle="$4"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/messages?limit=50" "$token") || smoke_fail "list messages failed"
  python3 -c 'import json,sys; needle=sys.argv[2]; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); sys.exit(0 if any(needle in (m.get("content") or "") for m in items) else 1)' "$resp" "$needle"
}

smoke_count_messages_with_prefix() {
  local base="$1" token="$2" chat_id="$3" prefix="$4"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/messages?limit=50" "$token") || smoke_fail "list messages failed"
  python3 -c 'import json,sys; prefix=sys.argv[2]; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); print(sum(1 for m in items if (m.get("content") or "").startswith(prefix)))' "$resp" "$prefix"
}

smoke_mark_read() {
  local base="$1" token="$2" chat_id="$3" msg_id="$4"
  local body
  body=$(python3 -c 'import json,sys; print(json.dumps({"message_ids":[sys.argv[1]]}))' "$msg_id")
  smoke_curl_json POST "${base%/}/api/v1/chats/${chat_id}/read-batch" "$token" "$body" >/dev/null \
    || smoke_fail "mark read failed"
}

smoke_read_receipt_has_user() {
  local base="$1" token="$2" chat_id="$3" msg_id="$4" user_id="$5"
  local resp
  resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/read-receipts?message_id=${msg_id}" "$token") \
    || smoke_fail "read-receipts failed"
  python3 -c 'import json,sys; uid=sys.argv[2]; d=json.loads(sys.argv[1]); readers=d.get("read_by") or []; sys.exit(0 if any((r.get("user_id") or r.get("id"))==uid for r in readers) else 1)' "$resp" "$user_id"
}

smoke_ws_wait_content() {
  local ws_url="$1" token="$2" needle="$3" timeout="${4:-20}"
  python3 - "$ws_url" "$token" "$needle" "$timeout" <<'PY'
import sys, threading
ws_url, token, needle, timeout_s = sys.argv[1:5]
timeout = float(timeout_s)
try:
    import websocket
except ImportError:
    sys.exit(2)

url = ws_url.rstrip("/") + "?token=" + token
got = threading.Event()

def on_message(ws, message):
    if needle in message:
        got.set()
        ws.close()

ws = websocket.WebSocketApp(url, on_message=on_message)
t = threading.Thread(target=lambda: ws.run_forever(ping_interval=20, ping_timeout=10), daemon=True)
t.start()
sys.exit(0 if got.wait(timeout) else 1)
PY
}

smoke_poll_message() {
  local base="$1" token="$2" chat_id="$3" needle="$4" timeout="${5:-15}"
  local i
  for ((i=0; i<timeout; i++)); do
    if smoke_messages_contain "$base" "$token" "$chat_id" "$needle"; then
      return 0
    fi
    sleep 1
  done
  return 1
}
