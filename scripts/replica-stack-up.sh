#!/usr/bin/env bash
# Apply read-replica lab overlay (spec 006 T305). Guest/CI only.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"
korus_set_path_env "$ROOT"
COMPOSE_FULL="$KORUS_COMPOSE_FULL_SERVER"
COMPOSE_REPLICA="$ROOT/docker/docker-compose.replica.yml"
cd "$ROOT"
if docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_REPLICA" up -d --no-build core-api 2>/dev/null; then
  :
elif sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_REPLICA" up -d --no-build core-api; then
  :
else
  docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_REPLICA" up -d core-api || \
    sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_REPLICA" up -d core-api
fi
echo "[OK] read-replica overlay applied (core-api DB_READ_JDBC_URL)"
