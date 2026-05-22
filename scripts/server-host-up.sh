#!/usr/bin/env bash
# Machine 1: full-server + lan-publish overlay.
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S]"
      echo "  Two-host dev server. See deploy/two-host/README.md"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 1 ;;
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

cd "$ROOT"
args=(-f "$KORUS_COMPOSE_FULL_SERVER" -f "$KORUS_COMPOSE_LAN_PUBLISH" up -d)
if "$BUILD"; then args+=(--build); fi
echo "docker compose ${args[*]}" >&2
korus_compose_up_multi_retry "${args[@]}" || exit 1

echo ""
echo "[OK] Server host (LAN publish): core-api :8080, Keycloak :8081, ws-gateway :8082"
echo "Open firewall 8080, 8082. Web machine: deploy/two-host/web.env.example -> korus-web/.env"
