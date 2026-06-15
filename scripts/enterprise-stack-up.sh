#!/usr/bin/env bash
# Enterprise profile: full-server + scale (+ optional read-replica lab). Guest/CI only.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENABLE_REPLICA="${KORUS_ENABLE_READ_REPLICA:-0}"
bash "$SCRIPT_DIR/full-stack-up.sh" --skip-ensure "$@"
bash "$SCRIPT_DIR/scale-stack-up.sh"
if [[ "$ENABLE_REPLICA" == "1" ]]; then
  bash "$SCRIPT_DIR/replica-stack-up.sh"
fi
echo "[OK] enterprise stack up (scale overlay; replica=${ENABLE_REPLICA})"
