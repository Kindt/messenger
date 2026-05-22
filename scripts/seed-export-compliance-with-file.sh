#!/usr/bin/env bash
# export-compliance-prep with --include-file.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
TEXT_COUNT="${TEXT_COUNT:-2}"
SKIP_PREP=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --text-count) TEXT_COUNT="$2"; shift 2 ;;
    --skip-prep) SKIP_PREP=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--text-count N] [--skip-prep]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

if $SKIP_PREP && [[ -z "$CHAT_ID" ]]; then
  fail "chat_id required with --skip-prep"
fi

prep_lines=()
if ! $SKIP_PREP; then
  prep_args=(--url "$BASE_URL" --message-count "$TEXT_COUNT" --include-file)
  [[ -n "$CHAT_ID" ]] && prep_args+=(--chat-id "$CHAT_ID" --no-create-group)
  mapfile -t prep_lines < <("${SCRIPT_DIR}/smoke-admin-export-compliance-prep.sh" "${prep_args[@]}")
  CHAT_ID="$(printf '%s\n' "${prep_lines[@]}" | sed -n 's/^CHAT_ID=//p' | tail -n 1)"
  [[ -n "$CHAT_ID" ]] || CHAT_ID="${prep_lines[0]:-}"
  [[ -n "$CHAT_ID" ]] || fail "prep did not return chat_id"
  echo "Waiting 2s (retention SELECT age buffer) ..." >&2
  sleep 2
fi

echo "[OK] chat with attachment seeded (via prep API)" >&2
echo "$CHAT_ID"
printf '%s\n' "${prep_lines[@]:-}" | sed -n 's/^\(FILE_ID=.*\)/\1/p' | tail -n 1
