#!/usr/bin/env bash
# ws-gateway WAR on Tomcat — smoke on QEMU server guest (metrics + WS probe).
set -euo pipefail

ROOT="${KORUS_ROOT:-/mnt/korus}"
IMAGE="${WS_GATEWAY_WAR_IMAGE:-korus-ws-gateway-war-smoke:local}"
CONTAINER="${WS_GATEWAY_WAR_CONTAINER:-korus-ws-gateway-war-smoke}"
HTTP_PORT="${WS_GATEWAY_WAR_HTTP_PORT:-18198}"
METRICS_PORT="${WS_GATEWAY_WAR_METRICS_PORT:-19200}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/war-smoke-common.sh
source "$SCRIPT_DIR/lib/war-smoke-common.sh"

cd "$ROOT"

if ! docker ps -q -f name=nats | grep -q .; then
  echo "[FAIL] nats not running; start full-server stack first" >&2
  exit 1
fi

NET=$(war_smoke_compose_network)
echo "Compose network: $NET"

echo "Building Docker image $IMAGE (Gradle WAR inside container) ..."
docker build -f docker/Dockerfile.ws-gateway.war -t "$IMAGE" .

docker rm -f "$CONTAINER" 2>/dev/null || true

echo "Starting WAR container (HTTP ${HTTP_PORT}, metrics ${METRICS_PORT}) ..."
docker run -d --name "$CONTAINER" --network "$NET" \
  -p "127.0.0.1:${HTTP_PORT}:8080" \
  -p "127.0.0.1:${METRICS_PORT}:9191" \
  -e WS_PORT=8080 \
  -e WS_METRICS_PORT=9191 \
  -e NATS_URL=nats://nats:4222 \
  -e DB_JDBC_URL=jdbc:postgresql://postgres-hot:5432/avandocmsg_hot \
  -e DB_USER=avandocmsg \
  -e DB_PASSWORD=avandocmsg \
  -e KEYCLOAK_ISSUER=http://keycloak:8080/realms/avandocmsg \
  -e KEYCLOAK_JWKS_URL=http://keycloak:8080/realms/avandocmsg/protocol/openid-connect/certs \
  -e APP_LOCALE=ru \
  "$IMAGE"

HEALTH_URL="http://127.0.0.1:${METRICS_PORT}/health"
if ! war_smoke_wait_http "$HEALTH_URL" 180; then
  echo "[FAIL] metrics health timeout: $HEALTH_URL" >&2
  docker logs "$CONTAINER" 2>&1 | tail -n 50 || true
  docker rm -f "$CONTAINER" 2>/dev/null || true
  exit 1
fi

WS_URL="http://127.0.0.1:${HTTP_PORT}/ws"
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  "$WS_URL" 2>/dev/null || echo "000")

case "$STATUS" in
  101|200|400|403) echo "[OK] WS probe $WS_URL HTTP $STATUS" ;;
  *)
    echo "[FAIL] WS probe $WS_URL HTTP $STATUS" >&2
    docker logs "$CONTAINER" 2>&1 | tail -n 30 || true
    docker rm -f "$CONTAINER" 2>/dev/null || true
    exit 1
    ;;
esac

docker rm -f "$CONTAINER" 2>/dev/null || true
echo "[OK] smoke-ws-gateway-war-guest"
