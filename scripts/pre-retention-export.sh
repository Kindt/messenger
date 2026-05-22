#!/usr/bin/env bash
# Queue chat export before aggressive retention (compliance workflow).
set -euo pipefail

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--url URL]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 1 ;;
  esac
done

[[ -n "$CHAT_ID" ]] || { echo "Missing --chat-id" >&2; exit 1; }

script_dir="$(cd "$(dirname "$0")" && pwd)"
export BASE_URL
"$script_dir/smoke-export-chat.sh" --url "$BASE_URL" --chat-id "$CHAT_ID"

echo ""
echo "Pre-retention checklist:"
echo "  1. Store downloaded artifact (JSON or ZIP) in your compliance archive."
echo "  2. For ZIP: verify attachments/manifest.json SHA-256; GET download?part=json|manifest."
echo "  3. Check export.json retentionPolicy and exportCompleteness.gdprDisclosures."
echo "  4. On export-replay-worker enable optional: EXPORT_REPLAY_INCLUDE_FILE_BODIES, _DEEP_ARCHIVE, _RETENTION_SNAPSHOTS, _SOLR_INDEX."
echo "  5. Admin audit: export.requested, export.downloaded, export.suggested / export.auto_queued."
echo "  6. Re-run export after retention policy changes if legal requires a point-in-time snapshot."
