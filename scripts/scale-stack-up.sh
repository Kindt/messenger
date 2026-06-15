#!/usr/bin/env bash
# Start full-server with scale overlay (2× pipeline + 2× ws-gateway). Guest/CI only.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"
korus_set_path_env "$ROOT"
COMPOSE_FULL="$KORUS_COMPOSE_FULL_SERVER"
COMPOSE_SCALE="$ROOT/docker/docker-compose.scale.yml"
cd "$ROOT"
if docker image inspect docker-message-pipeline:latest >/dev/null 2>&1; then
  docker tag docker-message-pipeline:latest docker-message-pipeline-2:latest 2>/dev/null || \
    sudo docker tag docker-message-pipeline:latest docker-message-pipeline-2:latest
fi
if docker image inspect docker-ws-gateway:latest >/dev/null 2>&1; then
  docker tag docker-ws-gateway:latest docker-ws-gateway-2:latest 2>/dev/null || \
    sudo docker tag docker-ws-gateway:latest docker-ws-gateway-2:latest
fi
if docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_SCALE" up -d --no-build message-pipeline-2 ws-gateway-2 2>/dev/null; then
  :
elif sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_SCALE" up -d --no-build message-pipeline-2 ws-gateway-2; then
  :
else
  docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_SCALE" up -d --build message-pipeline-2 ws-gateway-2 || \
    sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_SCALE" up -d --build message-pipeline-2 ws-gateway-2
fi
echo "[OK] scale stack up (message-pipeline-2, ws-gateway-2)"
