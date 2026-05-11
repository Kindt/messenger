#!/usr/bin/env bash
# docker/docker-compose.full-server.yml from repo root:
#   ./scripts/full-stack-up.sh [--build|-b] [--skip-ensure|-S]
# Sets KORUS_* env, ensure tooling, compose with retry.
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S]"
      echo "  Env SKIP_KORUS_ENSURE=1 also skips install-environment."
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

COMPOSE="$KORUS_COMPOSE_FULL_SERVER"
if [[ ! -f "$COMPOSE" ]]; then
  echo "Not found: $COMPOSE" >&2
  exit 1
fi

cd "$ROOT"
echo "cd $ROOT" >&2
if "$BUILD"; then
  echo "docker compose -f $COMPOSE up -d --build" >&2
  korus_compose_up_retry "$COMPOSE" up -d --build || exit 1
else
  echo "docker compose -f $COMPOSE up -d" >&2
  korus_compose_up_retry "$COMPOSE" up -d || exit 1
fi

echo ""
echo "[OK] Full stack: core-api :8080, Keycloak :8081, ws-gateway :8082, retention :9192"
echo "Admin: http://localhost:8080/admin/  (realm avandocmsg: csadmin/csadmin or admin/admin)"
echo "Attach korus-web: ./scripts/korus-web-up.sh --attach --build"
echo "Stop: ./scripts/full-stack-down.sh"
