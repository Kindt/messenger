#!/usr/bin/env bash
# Останавливает профиль web в docker/docker-compose.dev-min.yml.
# Из корня: ./scripts/dev-web-stack-down.sh [--volumes|-V] [--skip-ensure|-S]
set -euo pipefail

VOLUMES=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --volumes|-V) VOLUMES=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--volumes|-V] [--skip-ensure|-S]"
      echo "  docker compose ... --profile web down"
      echo "  korus-web UI stack: stop separately with ./scripts/korus-web-down.sh (--attach/--turn as for up)"
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
if "$VOLUMES"; then
  echo "docker compose -f $KORUS_COMPOSE_DEV_MIN --profile web down -v" >&2
  korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" --profile web down -v || exit 1
else
  echo "docker compose -f $KORUS_COMPOSE_DEV_MIN --profile web down" >&2
  korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" --profile web down || exit 1
fi

extra=""
if "$VOLUMES"; then
  extra=" (-v)"
fi
echo "[OK] Profile web stopped$extra"
echo "If korus-web was up with --attach: ./scripts/korus-web-down.sh --attach  (--turn if used)" >&2
