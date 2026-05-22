#!/bin/sh
set -eu
REPO=/mnt/korus
LOG=/var/log/korus-bootstrap.log
# API/ws on server VM are reachable from guest via QEMU user-net gateway (Windows host)
HOST_GW=10.0.2.2
exec >>"$LOG" 2>&1
echo "=== korus-web bootstrap $(date -Iseconds) ==="

for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if [ -f "$REPO/korus-web/docker-compose.yml" ]; then
    break
  fi
  echo "waiting for repo at $REPO..."
  sleep 3
done

if [ ! -f "$REPO/korus-web/docker-compose.yml" ]; then
  echo "ERROR: repo not mounted at $REPO"
  exit 1
fi

for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 24 30; do
  if curl -fsS "http://${HOST_GW}:18080/api/v1/health" >/dev/null 2>&1; then
    echo "server API ready via host ${HOST_GW}:18080"
    break
  fi
  echo "waiting for server API on ${HOST_GW}:18080..."
  sleep 10
done

LAN_IP=$(curl -fsS "http://${HOST_GW}:18890/host-lan-ip.txt" 2>/dev/null | tr -d '\r\n' || true)
if [ -z "$LAN_IP" ]; then
  LAN_IP=127.0.0.1
fi
echo "host LAN IP for browser WS: $LAN_IP"

ENV_FILE="$REPO/korus-web/.env"
cat >"$ENV_FILE" <<EOF
KORUS_WEB_LB_PORT=9088
KORUS_SERVER_HOST=${HOST_GW}
WEB_CLIENT_API_UPSTREAM=http://${HOST_GW}:18080
WEB_CLIENT_WS_PUBLIC_URL=ws://${LAN_IP}:19088/ws
KORUS_WS_GATEWAY_HOST=${HOST_GW}
KORUS_WS_GATEWAY_PORT=18082
EOF

cd "$REPO/korus-web"
docker compose --env-file .env -f docker-compose.yml up -d --build
echo "=== web stack up done ==="
