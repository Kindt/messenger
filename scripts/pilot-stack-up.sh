#!/usr/bin/env bash
# Deprecated wrapper — use scripts/lean-stack-up.sh (spec 021 T021-061).
echo "[DEPRECATED] pilot-stack-up.sh -> lean-stack-up.sh" >&2
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lean-stack-up.sh" "$@"
