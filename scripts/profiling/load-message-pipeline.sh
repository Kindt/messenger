#!/usr/bin/env bash
# Burst DM sends to estimate message-pipeline throughput (spec 006 T204).
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS="${SMOKE_USER_PASS:-smokepass123}"
BURST="${BURST:-20}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/SmokeMessaging.sh
source "$SCRIPT_DIR/../lib/SmokeMessaging.sh"

smoke_step "Login users A, B"
TOKEN_A=$(smoke_login "$BASE_URL" smoke_user_a "$PASS")
TOKEN_B=$(smoke_login "$BASE_URL" smoke_user_b "$PASS")
ID_B=$(smoke_user_id "$BASE_URL" "$TOKEN_B")
CHAT=$(smoke_create_p2p "$BASE_URL" "$TOKEN_A" "$ID_B")

smoke_step "Burst $BURST messages"
COUNT_BEFORE=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$CHAT")
START=$(date +%s)
LAST_ID=""
for i in $(seq 1 "$BURST"); do
  LAST_ID=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$CHAT" "load-burst-$i-$(date +%s%N)")
done
END=$(date +%s)
ELAPSED=$((END - START))
if [[ "$ELAPSED" -lt 1 ]]; then ELAPSED=1; fi
RATE=$((BURST / ELAPSED))
echo "[OK] sent $BURST messages in ${ELAPSED}s (~${RATE} msg/s burst average)"
TARGET_COUNT=$((COUNT_BEFORE + BURST))
smoke_poll_min_message_count "$BASE_URL" "$TOKEN_B" "$CHAT" "$TARGET_COUNT" 30 \
  || smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$CHAT" "$LAST_ID" 30 \
  || smoke_fail "delivery lag after burst"
echo "[OK] load-message-pipeline complete"
