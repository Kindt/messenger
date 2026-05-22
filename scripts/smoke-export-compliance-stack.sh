#!/usr/bin/env bash
# Up full stack with export overlays, run compliance pack, optional down.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

BUILD=false
AUTO_QUEUE=false
DOWN=false
SKIP_ENSURE=false
CHAT_ID=""
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true; shift ;;
    --auto-queue) AUTO_QUEUE=true; shift ;;
    --down) DOWN=true; shift ;;
    --skip-ensure|-S) SKIP_ENSURE=true; shift ;;
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--build] [--auto-queue] [--down] [--chat-id UUID]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

up_args=(--export-smoke)
$AUTO_QUEUE && up_args+=(--export-auto-queue)
$BUILD && up_args+=(--build)
$SKIP_ENSURE && up_args+=(--skip-ensure)

"${SCRIPT_DIR}/full-stack-up.sh" "${up_args[@]}"

pack_args=()
[[ -n "$CHAT_ID" ]] && pack_args+=(--chat-id "$CHAT_ID")
"${SCRIPT_DIR}/smoke-export-compliance-pack.sh" "${pack_args[@]}"

if $DOWN; then
  down_args=(--export-smoke)
  $AUTO_QUEUE && down_args+=(--export-auto-queue)
  "${SCRIPT_DIR}/full-stack-down.sh" "${down_args[@]}"
fi
