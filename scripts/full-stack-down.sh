#!/usr/bin/env bash
# Stops docker/docker-compose.full-server.yml (containers; volumes kept).
set -euo pipefail

EXPORT_SMOKE=false
EXPORT_AUTO_QUEUE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --export-smoke) EXPORT_SMOKE=true ;;
    --export-auto-queue) EXPORT_AUTO_QUEUE=true; EXPORT_SMOKE=true ;;
    -h|--help)
      echo "Usage: $0 [--export-smoke] [--export-auto-queue]"
      echo "  Use same flags as full-stack-up.sh when overlays were applied."
      echo "  After: stop korus-web separately if needed (./scripts/korus-web-down.sh)."
      exit 0
      ;;
    *) echo "Unknown argument: $1 (try --help)" >&2; exit 1 ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ ! -f "$KORUS_COMPOSE_FULL_SERVER" ]]; then
  echo "Not found: $KORUS_COMPOSE_FULL_SERVER" >&2
  exit 1
fi

cd "$ROOT"
compose_args=(-f "$KORUS_COMPOSE_FULL_SERVER")
if $EXPORT_SMOKE; then
  auto_flag=0
  $EXPORT_AUTO_QUEUE && auto_flag=1
  while IFS= read -r arg; do
    compose_args+=("$arg")
  done < <(korus_export_smoke_compose_args "$ROOT" "$auto_flag")
fi
compose_args+=(down)
echo "docker compose ${compose_args[*]}" >&2
korus_compose_up_multi_retry "${compose_args[@]}" || exit 1

echo "[OK] Full stack stopped."
echo "If korus-web was running: ./scripts/korus-web-down.sh  (--attach / --turn if used)" >&2
