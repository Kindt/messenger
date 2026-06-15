#!/usr/bin/env bash
# Platform W2 guest smokes (server guest). Optional export-replay gate.
set -euo pipefail
ROOT="${ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
API_URL="${KORUS_API_URL:-http://127.0.0.1:8080}"

echo "=== guest-smoke-platform-w2 (API=$API_URL) ==="

bash "$ROOT/scripts/verify-nats-queue-group.sh"

if [[ "${KORUS_RUN_EXPORT_PURGE_SMOKE:-0}" == "1" ]]; then
  echo "=== export-replay-before-purge (optional) ==="
  if command -v pwsh >/dev/null 2>&1; then
    pwsh -NoProfile -File "$ROOT/scripts/smoke-export-replay-before-purge.ps1" -BaseUrl "$API_URL"
  elif command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -File "$ROOT/scripts/smoke-export-replay-before-purge.ps1" -BaseUrl "$API_URL"
  else
    echo "[skip] PowerShell not available for export-replay smoke" >&2
    exit 1
  fi
else
  echo "[skip] export-replay (set KORUS_RUN_EXPORT_PURGE_SMOKE=1 to enable)"
fi

echo "[OK] guest-smoke-platform-w2"
