#!/usr/bin/env bash
# Lean stack acceptance smoke (spec 006 / FR-OPT-01). Canonical name (spec 021 T021-061).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
WS_URL="${WS_URL:-ws://127.0.0.1:8082/ws}"
PASS="${SMOKE_USER_PASS:-smokepass123}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --ws-url) WS_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--url BASE] [--ws-url WS]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

lean_send_legacy_text() {
  local base="$1" token="$2" chat_id="$3" content="$4"
  local body resp
  body=$(python3 -c 'import json,sys; print(json.dumps({"type":"text","content":sys.argv[1],"e2ee_scheme":"legacy"}))' "$content")
  resp=$(smoke_curl_json POST "${base%/}/api/v1/chats/${chat_id}/messages" "$token" "$body") || smoke_fail "send legacy message failed"
  python3 -c 'import json,sys; d=json.loads(sys.argv[1]); mid=d.get("id") or d.get("message_id"); (sys.exit(1) if not mid else None); print(mid)' "$resp" \
    || smoke_fail "legacy message id missing"
}

lean_poll_message_id() {
  local base="$1" token="$2" chat_id="$3" msg_id="$4" timeout="${5:-20}"
  local i resp
  for ((i=0; i<timeout; i++)); do
    resp=$(smoke_curl_json GET "${base%/}/api/v1/chats/${chat_id}/messages?limit=50" "$token") || smoke_fail "list messages failed"
    if python3 -c 'import json,sys; mid=sys.argv[2]; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("messages") or []); sys.exit(0 if any((m.get("id") or m.get("message_id"))==mid for m in items) else 1)' "$resp" "$msg_id"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

smoke_step "core-api health + ready"
curl -fsS "${BASE_URL%/}/api/v1/health" >/dev/null || smoke_fail "health"
curl -fsS "${BASE_URL%/}/api/v1/health/ready" >/dev/null || smoke_fail "ready"

smoke_step "No Solr/ZooKeeper containers"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qiE 'solr|zookeeper|zoo'; then
  smoke_fail "solr/zookeeper containers must not run on lean stack"
fi
echo "[OK] no solr/zookeeper" >&2

smoke_step "smoke-auth.sh"
BASE_URL="$BASE_URL" bash "$SCRIPT_DIR/smoke-auth.sh" --skip-logout

smoke_step "Ensure smoke users"
BASE_URL="$BASE_URL" bash "$SCRIPT_DIR/keycloak-ensure-smoke-users.sh"

smoke_step "DM: send + deliver"
TOKEN_A=$(smoke_login "$BASE_URL" smoke_user_a "$PASS")
TOKEN_B=$(smoke_login "$BASE_URL" smoke_user_b "$PASS")
ID_B=$(smoke_user_id "$BASE_URL" "$TOKEN_B")
DM_CHAT=$(smoke_create_p2p "$BASE_URL" "$TOKEN_A" "$ID_B")
MSG_ID=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$DM_CHAT" "lean-dm-$(date +%s)")
lean_poll_message_id "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$MSG_ID" 20 || smoke_fail "DM delivery failed"
echo "[OK] DM delivery" >&2

smoke_step "WS deliver"
WS_MARKER="lean-ws-$(date +%s)"
WS_PID=""
if command -v python3 >/dev/null 2>&1; then
  (
    smoke_ws_wait_content "$WS_URL" "$TOKEN_B" "$WS_MARKER" 25
  ) &
  WS_PID=$!
  sleep 2
  WS_MSG=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$DM_CHAT" "$WS_MARKER")
  if wait "$WS_PID"; then
    echo "[OK] WS delivery" >&2
  else
    lean_poll_message_id "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$WS_MSG" 15 \
      || smoke_fail "WS/REST delivery failed"
    echo "[OK] REST fallback delivery" >&2
  fi
else
  echo "[WARN] python3 missing; skip WS check" >&2
fi

smoke_step "SQL search (legacy plaintext)"
TOKEN_C=$(smoke_login "$BASE_URL" smoke_user_c "$PASS")
ID_C=$(smoke_user_id "$BASE_URL" "$TOKEN_C")
SEARCH_CHAT=$(smoke_create_p2p "$BASE_URL" "$TOKEN_A" "$ID_C")
MARKER="lean-search-$(date +%s)"
lean_send_legacy_text "$BASE_URL" "$TOKEN_A" "$SEARCH_CHAT" "$MARKER" >/dev/null
sleep 2
resp=$(smoke_curl_json GET "${BASE_URL%/}/api/v1/search/messages?q=lean-search&limit=20" "$TOKEN_A") \
  || smoke_fail "search/messages HTTP failed"
python3 -c 'import json,sys; data=json.loads(sys.argv[1]); items=data if isinstance(data,list) else (data.get("items") or data.get("results") or []); needle=sys.argv[2]; sys.exit(0 if any(needle in (m.get("content") or "") for m in items) else 1)' "$resp" "$MARKER" \
  || smoke_fail "search did not return lean message (SQL path)"
echo "[OK] SQL search returned results" >&2

echo ""
echo "[OK] smoke-lean-stack (spec 006)"
