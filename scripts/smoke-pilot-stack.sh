#!/usr/bin/env bash
# Deprecated wrapper — use scripts/smoke-lean-stack.sh (spec 021 T021-061).
echo "[DEPRECATED] smoke-pilot-stack.sh -> smoke-lean-stack.sh" >&2
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/smoke-lean-stack.sh" "$@"
