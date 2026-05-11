#!/bin/bash
# Unix/macOS. На Windows: scripts\create-stand.ps1 или scripts\create-stand.cmd
set -euo pipefail

STAND_TYPE="${1:-min}"

echo "=== Creating AvandocMsg stand: $STAND_TYPE ==="

case "$STAND_TYPE" in
    min)
        cd ../docker
        docker compose -f docker-compose.dev-min.yml pull
        docker compose -f docker-compose.dev-min.yml build
        echo "Stand 'min' created. Run: ./start.sh min  (Windows: ..\\scripts\\start.ps1 min)"
        ;;
    full)
        cd ../docker
        docker compose -f docker-compose.full-server.yml pull
        docker compose -f docker-compose.full-server.yml build
        echo "Stand 'full' (full-server) created. Run: ./start.sh full  (Windows: ..\\scripts\\start.ps1 full)"
        ;;
    *)
        echo "Usage: $0 {min|full}"
        exit 1
        ;;
esac
