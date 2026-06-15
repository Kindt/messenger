#!/usr/bin/env bash
# Apply Tier-2 replica-lab overlay (distinct read hostname). Guest/CI only.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"
korus_set_path_env "$ROOT"
COMPOSE_FULL="$KORUS_COMPOSE_FULL_SERVER"
COMPOSE_LAB="$ROOT/docker/docker-compose.replica-lab.yml"
cd "$ROOT"
if docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_LAB" up -d --no-build postgres-replica-lab core-api 2>/dev/null; then
  :
elif sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_LAB" up -d --no-build postgres-replica-lab core-api; then
  :
else
  docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_LAB" up -d postgres-replica-lab core-api || \
    sudo -E docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_LAB" up -d postgres-replica-lab core-api
fi
echo "[OK] replica-lab overlay (DB_READ -> postgres-replica-lab)"
