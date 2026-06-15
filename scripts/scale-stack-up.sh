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
docker compose -f "$COMPOSE_FULL" -f "$COMPOSE_SCALE" up -d "$@"
echo "[OK] scale stack up (message-pipeline-2, ws-gateway-2)"
