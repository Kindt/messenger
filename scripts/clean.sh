#!/bin/bash
# Unix/macOS. From repo root: ./scripts/clean.sh [min|full|all]
# Windows: scripts\clean.ps1
set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  echo "Usage: $0 [min|full|all]  (default: min)"
  echo "  min|full: docker compose down -v for that compose file."
  echo "  all: best-effort down for both stacks, then docker system prune -f."
  exit 0
fi

STAND_TYPE="${1:-min}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

echo "=== Cleaning AvandocMsg stand: $STAND_TYPE ==="

cd "$ROOT"

case "$STAND_TYPE" in
    min)
        korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" down -v || exit 1
        echo "Stand 'min' cleaned (volumes removed)"
        ;;
    full)
        korus_compose_file_retry "$KORUS_COMPOSE_FULL_SERVER" down -v || exit 1
        echo "Stand 'full' (full-server) cleaned (volumes removed)"
        ;;
    all)
        korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" down -v || true
        korus_compose_file_retry "$KORUS_COMPOSE_FULL_SERVER" down -v || true
        docker system prune -f || true
        echo "All stands cleaned (dev-min + full-server)"
        ;;
    *)
        echo "Usage: $0 {min|full|all}"
        exit 1
        ;;
esac
