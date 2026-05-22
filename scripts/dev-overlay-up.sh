#!/usr/bin/env bash
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S]"
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

if [[ ! -f "$KORUS_DEV_OVERLAY_DIR/webui/index.html" ]]; then
  echo "dev-overlay/webui empty — running dev-overlay-init.sh" >&2
  bash "$SCRIPT_DIR/dev-overlay-init.sh"
fi

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

KW="$KORUS_KORUS_WEB_DIR"
args=(compose)
[[ -f "$KW/.env" ]] && args+=(--env-file .env)
args+=(-f docker-compose.hotswap.yml up -d)
"$BUILD" && args+=(--build)

echo "cd $KW && docker ${args[*]}" >&2
korus_compose_in_dir_retry "$KW" "${args[@]}" || exit 1

echo ""
echo "[OK] Hot-swap web-dev — edit dev-overlay/webui/ and refresh browser"
