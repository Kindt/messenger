#!/bin/bash
# Rebuild and restart only core-api on QEMU server guest (Gradle layer in existing Dockerfile).
set -eu

REPO="${KORUS_REPO_ROOT:-/mnt/korus}"
COMPOSE="$REPO/docker/docker-compose.full-server.yml"
ENV_FILE="$REPO/docker/.env.korus-server"
LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"

exec >>"$LOG" 2>&1
echo "=== rebuild-core-api-guest.sh $(date -Iseconds) repo=$REPO ==="

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

echo ">>> docker compose build core-api (incremental Gradle in image)"
sudo docker compose -f "$COMPOSE" build core-api

echo ">>> docker compose up -d core-api"
sudo docker compose -f "$COMPOSE" up -d core-api

deadline=$((SECONDS + 300))
while [ "$SECONDS" -lt "$deadline" ]; do
  if curl -fsS --max-time 5 "http://127.0.0.1:8080/api/v1/health" >/dev/null 2>&1; then
    echo "core-api health OK"
    sudo docker ps --filter name=core-api --format 'core-api {{.Status}}'
    echo "=== rebuild-core-api-guest.sh done ==="
    exit 0
  fi
  sleep 5
done

echo "ERROR: core-api health timeout"
exit 1
