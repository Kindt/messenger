#!/usr/bin/env bash
# korus-web stack. From repo root: ./scripts/korus-web-up.sh [--attach|-a] [--build|-b] [--skip-ensure|-S]
set -euo pipefail

ATTACH=false
BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --attach|-a) ATTACH=true ;;
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--attach|-a] [--build|-b] [--skip-ensure|-S]"
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

if "$ATTACH"; then
  if ! docker network inspect korus_messenger_dev_min >/dev/null 2>&1; then
    echo "Warning: network korus_messenger_dev_min not found. Start dev-min first or set KORUS_DEV_MIN_NETWORK in korus-web/.env" >&2
  fi
fi

args=(compose)
if [[ -f "$KW/.env" ]]; then
  args+=(--env-file .env)
fi
args+=(-f docker-compose.yml)
if "$ATTACH"; then
  args+=(-f docker-compose.attach.yml)
fi
args+=(up -d)
if "$BUILD"; then
  args+=(--build)
fi

echo "cd $KW" >&2
echo "docker ${args[*]}" >&2
korus_compose_in_dir_retry "$KW" "${args[@]}" || exit 1

echo ""
if "$ATTACH"; then
  echo "[OK] korus-web up (attach)"
else
  echo "[OK] korus-web up"
fi
echo "Smoke: ./scripts/smoke-korus-web.sh --check-api"
echo "Quick check: curl -fsS http://localhost:9088/health"
bash "$ROOT/scripts/dev-ui-hints.sh"
