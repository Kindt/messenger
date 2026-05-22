#!/usr/bin/env bash
# Recreate retention-worker with export-suggested smoke overlay.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

BUILD=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"
korus_set_path_env "$ROOT"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) BUILD=true; shift ;;
    -h|--help) echo "Usage: $0 [--build]"; exit 0 ;;
    *) fail "Unknown: $1" ;;
  esac
done

overlay="$ROOT/docker/docker-compose.retention-export-smoke.yml"
[[ -f "$overlay" ]] || fail "Missing $overlay"

args=(compose -f "$KORUS_COMPOSE_FULL_SERVER" -f "$overlay" up -d retention-worker)
$BUILD && args+=(--build)

echo "docker ${args[*]} ..." >&2
(cd "$ROOT" && docker "${args[@]}")
echo "[OK] retention-worker smoke overlay applied" >&2
