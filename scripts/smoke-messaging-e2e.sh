#!/usr/bin/env bash
# Multi-user messaging E2E smoke (spec 003): DM, group, WS deliver, read receipts.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
WS_URL="${WS_URL:-ws://127.0.0.1:8082/ws}"
PASS="${SMOKE_USER_PASS:-smokepass123}"
SKIP_ENSURE_USERS=false
LOAD_ROUNDS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --ws-url) WS_URL="$2"; shift 2 ;;
    --skip-ensure-users) SKIP_ENSURE_USERS=true; shift ;;
    --load-rounds) LOAD_ROUNDS="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--url BASE] [--ws-url WS] [--skip-ensure-users] [--load-rounds N]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

if ! $SKIP_ENSURE_USERS; then
  smoke_step "Ensure smoke users"
  BASE_URL="$BASE_URL" bash "$SCRIPT_DIR/keycloak-ensure-smoke-users.sh"
fi

smoke_step "Login users A, B, C"
TOKEN_A=$(smoke_login "$BASE_URL" smoke_user_a "$PASS")
TOKEN_B=$(smoke_login "$BASE_URL" smoke_user_b "$PASS")
TOKEN_C=$(smoke_login "$BASE_URL" smoke_user_c "$PASS")
ID_A=$(smoke_user_id "$BASE_URL" "$TOKEN_A")
ID_B=$(smoke_user_id "$BASE_URL" "$TOKEN_B")
ID_C=$(smoke_user_id "$BASE_URL" "$TOKEN_C")
echo "user_a=$ID_A user_b=$ID_B user_c=$ID_C" >&2

smoke_step "DM: A creates p2p with B, sends 2 messages"
DM_CHAT=$(smoke_create_p2p "$BASE_URL" "$TOKEN_A" "$ID_B")
DM_M1=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$DM_CHAT" "smoke-dm-1-$(date +%s)")
DM_M2=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$DM_CHAT" "smoke-dm-2-$(date +%s)")
smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$DM_M1" 15 || smoke_fail "B did not see dm-1"
smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$DM_M2" 15 || smoke_fail "B did not see dm-2"
echo "[OK] DM delivery" >&2

smoke_step "Group: A creates [B,C], sends 3 messages"
GROUP_TITLE="smoke-group-$(date +%Y%m%d-%H%M%S)"
GROUP_CHAT=$(smoke_create_group "$BASE_URL" "$TOKEN_A" "$GROUP_TITLE" "$ID_B,$ID_C")
GRP_BEFORE_B=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT")
GRP_BEFORE_C=$(smoke_message_count "$BASE_URL" "$TOKEN_C" "$GROUP_CHAT")
GRP_TARGET_B=$((GRP_BEFORE_B + 3))
GRP_TARGET_C=$((GRP_BEFORE_C + 3))
G1="smoke-grp-1-$(date +%s)"
G2="smoke-grp-2-$(date +%s)"
G3="smoke-grp-3-$(date +%s)"
MSG_G1=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$G1")
smoke_send_message "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$G2" >/dev/null
MSG_G3=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$G3")
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  CB=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT")
  CC=$(smoke_message_count "$BASE_URL" "$TOKEN_C" "$GROUP_CHAT")
  if [[ "$CB" -ge "$GRP_TARGET_B" && "$CC" -ge "$GRP_TARGET_C" ]]; then break; fi
  sleep 1
done
CB=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT")
CC=$(smoke_message_count "$BASE_URL" "$TOKEN_C" "$GROUP_CHAT")
[[ "$CB" -ge "$GRP_TARGET_B" ]] || smoke_fail "B expected >=$GRP_TARGET_B group messages, got $CB"
[[ "$CC" -ge "$GRP_TARGET_C" ]] || smoke_fail "C expected >=$GRP_TARGET_C group messages, got $CC"
smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT" "$MSG_G3" 5 || true
echo "[OK] Group messages visible to B and C" >&2

smoke_step "Group: B replies"
REPLY=$(smoke_send_message "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT" "smoke-reply-$(date +%s)" "$MSG_G1")
smoke_poll_message_id "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$REPLY" 15 || smoke_fail "A did not see B reply"
echo "[OK] Group reply" >&2

smoke_step "WS: B connected, A sends ws-marker message"
WS_MARKER="smoke-ws-$(date +%s)"
WS_PID=""
if command -v python3 >/dev/null 2>&1; then
  (
    smoke_ws_wait_content "$WS_URL" "$TOKEN_B" "$WS_MARKER" 25
  ) &
  WS_PID=$!
  sleep 2
fi
smoke_send_message "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$WS_MARKER" >/dev/null
WS_OK=false
if [[ -n "$WS_PID" ]]; then
  if wait "$WS_PID"; then WS_OK=true; fi
fi
if ! $WS_OK; then
  echo "[WARN] WS deliver not confirmed; REST fallback poll" >&2
  smoke_poll_message "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT" "$WS_MARKER" 5 \
    || smoke_poll_min_message_count "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT" "$((GRP_TARGET_B + 2))" 20 \
    || smoke_fail "B did not receive ws-marker via REST either"
fi
echo "[OK] WS/REST delivery" >&2

smoke_step "Read receipts: B reads, A sees read_by"
smoke_mark_read "$BASE_URL" "$TOKEN_B" "$GROUP_CHAT" "$REPLY"
READ_OK=false
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if smoke_read_receipt_has_user "$BASE_URL" "$TOKEN_A" "$GROUP_CHAT" "$REPLY" "$ID_B"; then
    echo "[OK] read_by contains B" >&2
    READ_OK=true
    break
  fi
  sleep 1
done
$READ_OK || smoke_fail "read_by did not include B for message $REPLY"

if [[ "$LOAD_ROUNDS" -gt 0 ]]; then
  smoke_step "Load: $LOAD_ROUNDS extra DM rounds"
  DM_LOAD_BEFORE=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$DM_CHAT")
  LAST_LOAD_ID=""
  for r in $(seq 1 "$LOAD_ROUNDS"); do
    LAST_LOAD_ID=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$DM_CHAT" "smoke-load-$r-$(date +%s)")
  done
  smoke_poll_min_message_count "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$((DM_LOAD_BEFORE + LOAD_ROUNDS))" 30 \
    || smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$DM_CHAT" "$LAST_LOAD_ID" 30 \
    || smoke_fail "load round delivery failed"
  echo "[OK] load rounds complete" >&2
fi

echo ""
echo "[OK] smoke-messaging-e2e (spec 003)"
exit 0
