#!/bin/bash
# pull + build. From repo root:
#   ./scripts/create-stand.sh [--skip-ensure|-S] [min|full]
#   SKIP_KORUS_ENSURE=1 ./scripts/create-stand.sh min
# Windows: scripts\create-stand.ps1 or scripts\create-stand.cmd min -SkipEnsure
set -euo pipefail

STAND_TYPE=""
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-ensure|-S)
      SKIP_KORUS_ENSURE=1
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--skip-ensure|-S] [min|full]"
      echo "  Prepare images (docker compose pull && build). Default stand: min."
      echo "  --skip-ensure / -S or env SKIP_KORUS_ENSURE=1 skips install-environment."
      exit 0
      ;;
    min|full)
      STAND_TYPE="$1"
      shift
      ;;
    *)
      echo "Unknown option or stand: $1 (try --help)" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$STAND_TYPE" ]]; then
  STAND_TYPE="min"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

echo "=== Creating AvandocMsg stand: $STAND_TYPE ==="

cd "$ROOT"

case "$STAND_TYPE" in
    min)
        korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" pull || exit 1
        korus_compose_file_retry "$KORUS_COMPOSE_DEV_MIN" build || exit 1
        echo "Stand 'min' created. Run: ./scripts/start.sh min  (Windows: .\\scripts\\start.ps1 min)"
        ;;
    full)
        korus_compose_file_retry "$KORUS_COMPOSE_FULL_SERVER" pull || exit 1
        korus_compose_file_retry "$KORUS_COMPOSE_FULL_SERVER" build || exit 1
        echo "Stand 'full' (full-server) created. Run: ./scripts/start.sh full  (Windows: .\\scripts\\start.ps1 full)"
        ;;
    *)
        echo "Usage: $0 [--skip-ensure|-S] [min|full]" >&2
        exit 1
        ;;
esac
