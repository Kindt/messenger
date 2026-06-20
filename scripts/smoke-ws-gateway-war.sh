#!/usr/bin/env bash
# Smoke ws-gateway WAR or embedded: metrics /health + lightweight WebSocket upgrade probe.
set -euo pipefail

HEALTH_URL="${WS_GATEWAY_HEALTH_URL:-}"
WS_HTTP_URL="${WS_GATEWAY_WS_HTTP_URL:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      echo "Usage: $0"
      echo "Env:"
      echo "  WS_GATEWAY_HEALTH_URL  metrics /health (default: http://localhost:9191/health, then :9198)"
      echo "  WS_GATEWAY_WS_HTTP_URL HTTP base for /ws upgrade (default: http://localhost:8080/ws, then :8082/ws)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -n "$HEALTH_URL" ]]; then
  health_urls=("$HEALTH_URL")
else
  health_urls=("http://localhost:9191/health" "http://localhost:9198/health")
fi

health_ok=0
for u in "${health_urls[@]}"; do
  if body=$(curl -fsS "$u" 2>/dev/null); then
    body_trim=$(echo -n "$body" | tr -d '\r\n')
    if [[ "$body_trim" == "ok" ]]; then
      echo "[OK] ws-gateway metrics health ($u)"
      health_ok=1
      break
    fi
  fi
done

if [[ "$health_ok" -ne 1 ]]; then
  echo "[FAIL] ws-gateway metrics health checks failed for: ${health_urls[*]}" >&2
  exit 1
fi

if [[ -n "$WS_HTTP_URL" ]]; then
  ws_urls=("$WS_HTTP_URL")
else
  ws_urls=("http://localhost:8080/ws" "http://localhost:8082/ws" "http://localhost:8081/ws")
fi

ws_ok=0
for ws in "${ws_urls[@]}"; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Connection: Upgrade' \
    -H 'Upgrade: websocket' \
    -H 'Sec-WebSocket-Version: 13' \
    -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
    "$ws" 2>/dev/null || true)
  if [[ "$status" == "101" || "$status" == "200" || "$status" == "400" || "$status" == "403" ]]; then
    echo "[OK] ws-gateway WebSocket endpoint reachable ($ws, HTTP $status)"
    ws_ok=1
    break
  fi
done

if [[ "$ws_ok" -ne 1 ]]; then
  echo "[FAIL] ws-gateway WebSocket upgrade probe failed for: ${ws_urls[*]}" >&2
  exit 1
fi

echo "[OK] ws-gateway WAR smoke passed"
