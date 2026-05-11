#!/bin/bash
# Unix/macOS. From repo root: ./scripts/start.sh [min|full]
# Sets KORUS_* env, runs install-environment / install-env-silent if needed, docker compose (2 tries).
# Windows: scripts\start.ps1
# Skip tooling: SKIP_KORUS_ENSURE=1 ./scripts/start.sh
set -euo pipefail

STAND_TYPE="${1:-min}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "${SKIP_KORUS_ENSURE:-0}" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

echo "=== Starting AvandocMsg stand: $STAND_TYPE ==="
echo "KORUS_REPO_ROOT=$KORUS_REPO_ROOT"

cd "$ROOT"

case "$STAND_TYPE" in
    min)
        korus_compose_up_retry "$KORUS_COMPOSE_DEV_MIN" up -d || exit 1
        echo "Waiting for services..."
        sleep 5
        echo "Core API: http://localhost:8080/api/v1/health"
        echo "KeyCloak: http://localhost:8081 (admin/admin)"
        echo "Solr:     http://localhost:8983"
        echo "MinIO:    http://localhost:9001 (avandocmsg/avandocmsg123)"
        echo "NATS:     nats://localhost:4222"
        echo "Redis:    redis://localhost:6379"
        echo "PG Hot:   postgres://localhost:5432 (avandocmsg/avandocmsg)"
        echo "PG Arch:  postgres://localhost:5433 (avandocmsg/avandocmsg)"
        ;;
    full)
        korus_compose_up_retry "$KORUS_COMPOSE_FULL_SERVER" up -d || exit 1
        echo "Stand 'full' (full-server) started"
        ;;
    *)
        echo "Usage: $0 {min|full}"
        exit 1
        ;;
esac
