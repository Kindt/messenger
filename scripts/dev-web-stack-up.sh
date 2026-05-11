#!/usr/bin/env bash
# docker/docker-compose.dev-min.yml with profile web. From repo root: ./scripts/dev-web-stack-up.sh [--build]
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    -h|--help)
      echo "Usage: $0 [--build|-b]  (SKIP_KORUS_ENSURE=1 to skip install check)"
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

if [[ ! -f "$KORUS_COMPOSE_DEV_MIN" ]]; then
  echo "Not found: $KORUS_COMPOSE_DEV_MIN" >&2
  exit 1
fi

cd "$ROOT"
echo "cd $ROOT" >&2
if "$BUILD"; then
  echo "docker compose -f $KORUS_COMPOSE_DEV_MIN --profile web up -d --build" >&2
  korus_compose_up_retry "$KORUS_COMPOSE_DEV_MIN" --profile web up -d --build || exit 1
else
  echo "docker compose -f $KORUS_COMPOSE_DEV_MIN --profile web up -d" >&2
  korus_compose_up_retry "$KORUS_COMPOSE_DEV_MIN" --profile web up -d || exit 1
fi

echo ""
echo "[OK] Profile web: ws-gateway (host :8082), message-pipeline"
echo "Next UI: ./scripts/korus-web-up.sh --build  (or PowerShell: .\\scripts\\korus-web-up.ps1 -Build)"
echo "Attach UI to dev-min: ./scripts/korus-web-up.sh --attach --build  (network korus_messenger_dev_min; see korus-web/README.md)"
echo "Smoke: ./scripts/smoke-korus-web.sh --check-api  (or .\\scripts\\smoke-korus-web.ps1 -CheckApi)"
echo "Core API direct: curl -fsS http://localhost:8080/api/v1/health"
bash "$ROOT/scripts/dev-ui-hints.sh"
echo "Then run ./scripts/korus-web-up.sh --build and open the web client URL from the hints above."
