#!/usr/bin/env bash
# Останавливает docker/docker-compose.full-server.yml
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$ROOT/docker/docker-compose.full-server.yml"

if [[ ! -f "$COMPOSE" ]]; then
  echo "Not found: $COMPOSE" >&2
  exit 1
fi

echo "docker compose -f $COMPOSE down" >&2
cd "$ROOT"
docker compose -f "$COMPOSE" down

echo "[OK] Stack stopped."
