#!/usr/bin/env bash
# core-api WAR on Tomcat — smoke against live compose network (QEMU server guest).
set -euo pipefail

ROOT="${KORUS_ROOT:-/mnt/korus}"
IMAGE="${CORE_API_WAR_IMAGE:-korus-core-api-war-smoke:local}"
CONTAINER="${CORE_API_WAR_CONTAINER:-korus-core-api-war-smoke}"
HOST_PORT="${CORE_API_WAR_HOST_PORT:-18099}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/war-smoke-common.sh
source "$SCRIPT_DIR/lib/war-smoke-common.sh"

cd "$ROOT"

if ! docker ps -q -f name=postgres-hot | grep -q .; then
  echo "[FAIL] postgres-hot not running; start full-server stack first" >&2
  exit 1
fi

NET=$(war_smoke_compose_network)
echo "Compose network: $NET"

echo "Building Docker image $IMAGE (Gradle WAR inside container) ..."
docker build -f docker/Dockerfile.core-api.war -t "$IMAGE" .

docker rm -f "$CONTAINER" 2>/dev/null || true

echo "Starting WAR container on 127.0.0.1:${HOST_PORT} ..."
docker run -d --name "$CONTAINER" --network "$NET" \
  -p "127.0.0.1:${HOST_PORT}:8080" \
  -e APP_PORT=8080 \
  -e DB_JDBC_URL=jdbc:postgresql://postgres-hot:5432/avandocmsg_hot \
  -e DB_USER=avandocmsg \
  -e DB_PASSWORD=avandocmsg \
  -e REDIS_URI=redis://redis:6379 \
  -e NATS_URL=nats://nats:4222 \
  -e KEYCLOAK_ISSUER=http://keycloak:8080/realms/avandocmsg \
  -e KEYCLOAK_JWKS_URL=http://keycloak:8080/realms/avandocmsg/protocol/openid-connect/certs \
  -e KEYCLOAK_MASTER_USER=admin \
  -e KEYCLOAK_MASTER_PASSWORD=admin \
  -e MINIO_ENDPOINT=http://minio:9000 \
  -e MINIO_ACCESS_KEY=avandocmsg \
  -e MINIO_SECRET_KEY=avandocmsg123 \
  -e MINIO_BUCKET=avandocmsg \
  -e EXPORT_DIR=/export \
  -e KORUS_DEFAULT_ORG_ID=11111111-1111-4111-8111-111111111111 \
  "$IMAGE"

HEALTH_URL="http://127.0.0.1:${HOST_PORT}/api/v1/health"
if ! war_smoke_wait_http "$HEALTH_URL" 240; then
  echo "[FAIL] health timeout: $HEALTH_URL" >&2
  docker logs "$CONTAINER" 2>&1 | tail -n 50 || true
  docker exec "$CONTAINER" sh -c 'tail -n 30 /usr/local/tomcat/logs/localhost.*.log 2>/dev/null' || true
  docker rm -f "$CONTAINER" 2>/dev/null || true
  exit 1
fi

BODY=$(curl -fsS "$HEALTH_URL")
echo "[OK] GET $HEALTH_URL -> $BODY"

docker rm -f "$CONTAINER" 2>/dev/null || true
echo "[OK] smoke-core-api-war-guest"
