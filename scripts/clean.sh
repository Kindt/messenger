#!/bin/bash
# Unix/macOS. На Windows: scripts\clean.ps1 или scripts\clean.cmd
set -euo pipefail

STAND_TYPE="${1:-min}"

echo "=== Cleaning AvandocMsg stand: $STAND_TYPE ==="

case "$STAND_TYPE" in
    min)
        cd ../docker
        docker compose -f docker-compose.dev-min.yml down -v
        echo "Stand 'min' cleaned (volumes removed)"
        ;;
    full)
        cd ../docker
        docker compose -f docker-compose.full-server.yml down -v
        echo "Stand 'full' (full-server) cleaned (volumes removed)"
        ;;
    all)
        cd ../docker
        docker compose -f docker-compose.dev-min.yml down -v 2>/dev/null || true
        docker compose -f docker-compose.full-server.yml down -v 2>/dev/null || true
        docker system prune -f
        echo "All stands cleaned (dev-min + full-server)"
        ;;
    *)
        echo "Usage: $0 {min|full|all}"
        exit 1
        ;;
esac
