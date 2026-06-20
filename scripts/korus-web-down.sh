#!/usr/bin/env bash
# Останавливает korus-web с теми же compose-файлами, что и korus-web-up.sh.
# Из корня: ./scripts/korus-web-down.sh [--attach|-a] [--turn|-t] [--volumes|-V] [--skip-ensure|-S]
set -euo pipefail

ATTACH=false
TURN=false
VOLUMES=false
LEGACY_JAVA=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --attach|-a) ATTACH=true ;;
    --turn|-t) TURN=true ;;
    --volumes|-V) VOLUMES=true ;;
    --legacy-java-replicas) LEGACY_JAVA=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--attach|-a] [--turn|-t] [--volumes|-V] [--legacy-java-replicas] [--skip-ensure|-S]"
      echo "  --volumes: docker compose down -v"
      echo "  Use the same --attach/--turn as for korus-web-up.sh."
      echo "  Only ws-gateway from dev-min (--profile web), no korus-web: ./scripts/dev-web-stack-down.sh"
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
KW="$ROOT/korus-web"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

if [[ ! -f "$KORUS_KORUS_WEB_COMPOSE" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE" >&2
  exit 1
fi
if "$ATTACH" && [[ ! -f "$KORUS_KORUS_WEB_COMPOSE_ATTACH" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE_ATTACH" >&2
  exit 1
fi
if "$TURN" && [[ ! -f "$KORUS_KORUS_WEB_COMPOSE_TURN" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE_TURN" >&2
  exit 1
fi

args=(compose)
if [[ -f "$KW/.env" ]]; then
  args+=(--env-file .env)
fi
args+=(-f docker-compose.yml)
if "$LEGACY_JAVA"; then
  args+=(--profile legacy-java-replicas)
else
  args+=(-f docker-compose.nginx-only.yml)
fi
if "$ATTACH"; then
  args+=(-f docker-compose.attach.yml)
fi
if "$TURN"; then
  args+=(-f docker-compose.turn.yml)
fi
args+=(down)
if "$VOLUMES"; then
  args+=(-v)
fi

echo "cd $KW" >&2
echo "docker ${args[*]}" >&2
korus_compose_in_dir_retry "$KW" "${args[@]}" || exit 1

extra=""
if "$VOLUMES"; then
  extra=" (-v)"
fi
if "$ATTACH" && "$TURN"; then
  echo "[OK] korus-web down (attach + turn)$extra"
elif "$ATTACH"; then
  echo "[OK] korus-web down (attach)$extra"
elif "$TURN"; then
  echo "[OK] korus-web down (+ turn)$extra"
else
  echo "[OK] korus-web down$extra"
fi
