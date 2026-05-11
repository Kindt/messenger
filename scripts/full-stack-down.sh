#!/usr/bin/env bash
# Stops docker/docker-compose.full-server.yml (containers; volumes kept).
set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  echo "Usage: $0"
  echo "  Runs docker compose down on full-server.yml (no -v). No other arguments."
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "Unknown argument: $1 (try --help)" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ ! -f "$KORUS_COMPOSE_FULL_SERVER" ]]; then
  echo "Not found: $KORUS_COMPOSE_FULL_SERVER" >&2
  exit 1
fi

cd "$ROOT"
echo "docker compose -f $KORUS_COMPOSE_FULL_SERVER down" >&2
korus_compose_file_retry "$KORUS_COMPOSE_FULL_SERVER" down || exit 1

echo "[OK] Full stack stopped."
