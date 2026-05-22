#!/usr/bin/env bash
# Admin: GET /api/v1/admin/export/jobs (global list with optional filters).
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-csadmin}"
ADMIN_PASS="${ADMIN_PASS:-csadmin}"
CHAT_ID="${CHAT_ID:-}"
STATUS="${STATUS:-}"
LIMIT="${LIMIT:-10}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/smoke-export-audit.sh
source "$SCRIPT_DIR/lib/smoke-export-audit.sh" 2>/dev/null || true

token="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("access_token") or d.get("accessToken",""))')"
[[ -n "$token" ]] || { echo "No admin token"; exit 1; }

uri="$BASE_URL/api/v1/admin/export/jobs?limit=$LIMIT"
[[ -n "$CHAT_ID" ]] && uri+="&chat_id=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$CHAT_ID")"
[[ -n "$STATUS" ]] && uri+="&status=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$STATUS")"

echo "GET $uri"
resp="$(curl -sf -H "Authorization: Bearer $token" "$uri")"
echo "$resp" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert "jobs" in d; print("[OK] job_count=", d.get("job_count"), "filter=", d.get("status_filter"), "chat=", d.get("chat_id_filter"))'
