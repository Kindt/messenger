#!/usr/bin/env bash
# POST /api/v1/admin/export-compliance-prep. Requires EXPORT_ADMIN_SUGGEST_ENABLED=true.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
MESSAGE_COUNT=3
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
CREATE_GROUP=true
INCLUDE_FILE=false
FILE_NAME="compliance-smoke.txt"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; CREATE_GROUP=false; shift 2 ;;
    --message-count|-n) MESSAGE_COUNT="$2"; shift 2 ;;
    --include-file) INCLUDE_FILE=true; shift ;;
    --file-name) FILE_NAME="$2"; shift 2 ;;
    --no-create-group) CREATE_GROUP=false; shift ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--message-count N] [--no-create-group] [--url URL]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

command -v python3 >/dev/null 2>&1 || fail "python3 required"

token() {
  python3 -c '
import json, sys, urllib.request
base, user, pw = sys.argv[1], sys.argv[2], sys.argv[3]
req = urllib.request.Request(
  base + "/api/v1/auth/login",
  data=json.dumps({"username": user, "password": pw}).encode(),
  headers={"Content-Type": "application/json"},
  method="POST",
)
with urllib.request.urlopen(req) as r:
  d = json.load(r)
print(d.get("access_token") or d.get("accessToken") or "")
' "$BASE_URL" "$KORUS_USER" "$KORUS_PASS"
}

export BASE_URL TOK MESSAGE_COUNT CREATE_GROUP CHAT_ID INCLUDE_FILE FILE_NAME
TOK="$(token)"
[[ -n "$TOK" ]] || fail "login failed"

python3 -c '
import json, os, sys, urllib.error, urllib.request

base = os.environ["BASE_URL"]
tok = os.environ["TOK"]
chat_id = os.environ.get("CHAT_ID", "")
create_group = os.environ.get("CREATE_GROUP", "true").lower() == "true"
count = int(os.environ.get("MESSAGE_COUNT", "3"))

body = {"message_count": count, "create_group": create_group and not chat_id}
if chat_id:
    body["chat_id"] = chat_id
if os.environ.get("INCLUDE_FILE", "false").lower() == "true":
    body["include_file"] = True
    body["file_name"] = os.environ.get("FILE_NAME", "compliance-smoke.txt")

req = urllib.request.Request(
    base + "/api/v1/admin/export-compliance-prep",
    data=json.dumps(body).encode(),
    headers={"Authorization": "Bearer " + tok, "Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req) as r:
        prep = json.load(r)
except urllib.error.HTTPError as e:
    if e.code == 404:
        print("[FAIL] disabled? Set EXPORT_ADMIN_SUGGEST_ENABLED=true", file=sys.stderr)
    raise

cid = prep.get("chat_id") or prep.get("chatId") or ""
ids = prep.get("message_ids") or prep.get("messageIds") or []
fid = prep.get("file_id") or prep.get("fileId") or ""
print(f"[OK] chat_id={cid} messages={len(ids)} retention_patched={prep.get('retention_patched')} file_id={fid}")
if cid:
    print(f"CHAT_ID={cid}")
if fid:
    print(f"FILE_ID={fid}")
'
