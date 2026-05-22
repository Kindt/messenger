#!/usr/bin/env bash
set -euo pipefail
FORCE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force|-f) FORCE=true ;;
    -h|--help)
      echo "Usage: $0 [--force|-f]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 1 ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$ROOT/modules/web-client/src/main/resources/webui"
DST="$ROOT/dev-overlay/webui"
mkdir -p "$DST"
for f in index.html app.js styles.css; do
  if [[ -f "$DST/$f" && "$FORCE" != true ]]; then
    echo "Skip (exists): $DST/$f  (--force to overwrite)"
  else
    cp "$SRC/$f" "$DST/$f"
    echo "Copied: $f"
  fi
done
echo "[OK] dev-overlay ready. Edit dev-overlay/webui/, then ./scripts/dev-overlay-up.sh"
