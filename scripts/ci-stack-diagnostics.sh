#!/usr/bin/env bash
# CI failure dump: compose ps + tail logs for smoke workflows.
set +e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-full-server}"

compose=( -f "$ROOT/docker/docker-compose.full-server.yml" )
case "$MODE" in
  full-server) ;;
  export-smoke)
    compose+=(
      -f "$ROOT/docker/docker-compose.export-smoke.yml"
      -f "$ROOT/docker/docker-compose.retention-export-smoke.yml"
      -f "$ROOT/docker/docker-compose.export-auto-queue-smoke.yml"
    )
    ;;
  *)
    echo "Unknown mode: $MODE (use full-server or export-smoke)" >&2
    exit 2
    ;;
esac

echo "=== docker compose ps ($MODE) ===" >&2
docker compose "${compose[@]}" ps 2>&1

for svc in core-api export-replay-worker retention-worker ws-gateway keycloak postgres-hot minio nats; do
  echo "" >&2
  echo "=== logs: $svc (tail 80) ===" >&2
  docker compose "${compose[@]}" logs --tail=80 "$svc" 2>&1
done

if [[ -f /tmp/korus-bootstrap.log ]]; then
  echo "" >&2
  echo "=== /tmp/korus-bootstrap.log (tail 80) ===" >&2
  tail -n 80 /tmp/korus-bootstrap.log 2>&1
fi
