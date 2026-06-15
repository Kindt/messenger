#!/bin/bash
# Rebuild Wave 3 workers on QEMU server guest (pipeline, indexer, deep-archiver).
set -eu

REPO="${KORUS_REPO_ROOT:-/mnt/korus}"
COMPOSE="$REPO/docker/docker-compose.full-server.yml"
ENV_FILE="$REPO/docker/.env.korus-server"
LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"
WORKERS=(message-pipeline indexer-worker deep-archiver-worker)

exec >>"$LOG" 2>&1
echo "=== rebuild-workers-guest.sh $(date -Iseconds) repo=$REPO ==="

if [ ! -f "$COMPOSE" ]; then
  echo "ERROR: missing $COMPOSE"
  exit 1
fi

if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-plain-build-env.sh" ]; then
  # shellcheck source=/dev/null
  . "$REPO/deploy/qemu/vm-bootstrap/korus-plain-build-env.sh"
fi

cd "$REPO"
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck source=/dev/null
  . "$ENV_FILE"
  set +a
fi

echo ">>> docker compose build ${WORKERS[*]}"
sudo docker compose -f "$COMPOSE" build "${WORKERS[@]}"

echo ">>> docker compose up -d ${WORKERS[*]}"
sudo docker compose -f "$COMPOSE" up -d "${WORKERS[@]}"

COMPOSE_SCALE="$REPO/docker/docker-compose.scale.yml"
if [ -f "$COMPOSE_SCALE" ]; then
  if sudo docker image inspect docker-message-pipeline:latest >/dev/null 2>&1; then
    sudo docker tag docker-message-pipeline:latest docker-message-pipeline-2:latest
  fi
  if sudo docker image inspect docker-ws-gateway:latest >/dev/null 2>&1; then
    sudo docker tag docker-ws-gateway:latest docker-ws-gateway-2:latest
  fi
  if sudo docker ps --format '{{.Names}}' | grep -q '^docker-message-pipeline-2-1$'; then
    echo ">>> refresh scale replicas (message-pipeline-2, ws-gateway-2)"
    sudo docker compose -f "$COMPOSE" -f "$COMPOSE_SCALE" up -d --no-build --force-recreate message-pipeline-2 ws-gateway-2
  fi
fi

for svc in "${WORKERS[@]}"; do
  sudo docker ps --filter "name=$svc" --format "$svc {{.Status}}" | head -1
done

echo "=== rebuild-workers-guest.sh done ==="
