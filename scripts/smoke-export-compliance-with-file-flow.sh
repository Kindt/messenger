#!/usr/bin/env bash
# DEPRECATED alias (kept for backward compatibility).
# Canonical path: smoke-export-compliance-flow.sh --include-file
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
args=(--include-file)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) args+=(--chat-id "$2"); shift 2 ;;
    --url|-u) args+=(--url "$2"); shift 2 ;;
    --poll-seconds) args+=(--poll-seconds "$2"); shift 2 ;;
    --skip-prep) args+=(--skip-prep); shift ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--poll-seconds N] [--skip-prep] [--url URL]"
      echo "  Delegates to smoke-export-compliance-flow.sh --include-file"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 1 ;;
  esac
done
exec "${SCRIPT_DIR}/smoke-export-compliance-flow.sh" "${args[@]}"
