#!/usr/bin/env bash
# Synthetic fan-out load (PS-4.1) — burst DM sends, verify delivery + pipeline metrics.
# Run inside server guest (docker compose network) or with BASE_URL pointing at API.
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS="${SMOKE_USER_PASS:-smokepass123}"
BURST="${BURST:-100}"
DURATION_SEC="${DURATION_SEC:-120}"
RATE_TARGET="${RATE_TARGET:-50}"
METRICS_URL="${PIPELINE_METRICS_URL:-http://127.0.0.1:9197/metrics}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

smoke_step "Login users A, B"
TOKEN_A=$(smoke_login "$BASE_URL" smoke_user_a "$PASS")
TOKEN_B=$(smoke_login "$BASE_URL" smoke_user_b "$PASS")
ID_B=$(smoke_user_id "$BASE_URL" "$TOKEN_B")
CHAT=$(smoke_create_p2p "$BASE_URL" "$TOKEN_A" "$ID_B")

smoke_step "Burst $BURST messages (fan-out / pipeline)"
COUNT_BEFORE=$(smoke_message_count "$BASE_URL" "$TOKEN_B" "$CHAT")
START=$(date +%s%N)
LAST_ID=""
for i in $(seq 1 "$BURST"); do
  LAST_ID=$(smoke_send_message "$BASE_URL" "$TOKEN_A" "$CHAT" "fanout-load-$i-$(date +%s%N)")
done
END=$(date +%s%N)
ELAPSED_MS=$(( (END - START) / 1000000 ))
if [[ "$ELAPSED_MS" -lt 1 ]]; then ELAPSED_MS=1; fi
RATE=$(( BURST * 1000 / ELAPSED_MS ))
echo "[OK] sent $BURST messages in ${ELAPSED_MS}ms (~${RATE} msg/s burst)"

TARGET_COUNT=$((COUNT_BEFORE + BURST))
smoke_poll_min_message_count "$BASE_URL" "$TOKEN_B" "$CHAT" "$TARGET_COUNT" 60 \
  || smoke_poll_message_id "$BASE_URL" "$TOKEN_B" "$CHAT" "$LAST_ID" 60 \
  || smoke_fail "delivery lag after fanout burst"

if curl -sf "$METRICS_URL" -o /tmp/pipeline-metrics.txt 2>/dev/null; then
  grep -E '^pipeline_fanout_recipients_' /tmp/pipeline-metrics.txt | head -5 || true
  echo "[OK] pipeline metrics sampled from $METRICS_URL"
else
  echo "[WARN] pipeline metrics unavailable at $METRICS_URL (set PIPELINE_METRICS_URL)" >&2
fi

echo "[OK] load-fanout-synthetic complete (target ~${RATE_TARGET} msg/s sustained on guest - extend BURST/DURATION)"
echo "Live gate: pipeline CPU < 80%, no NATS slow consumer — inspect guest docker stats / nats logs"
