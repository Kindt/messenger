#!/bin/bash
set -euo pipefail
cd /mnt/korus/docker
export DOCKER_BUILDKIT=0
docker compose -f docker-compose.full-server.yml build ws-gateway
docker compose -f docker-compose.full-server.yml up -d --no-deps ws-gateway
echo ws-gateway-rebuild-done
