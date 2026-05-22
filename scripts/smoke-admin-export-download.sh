#!/usr/bin/env bash
# GET admin export bundle for latest finished job or explicit job_id.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
JOB_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
PART="${PART:-bundle}"
REQUIRE_SUCCESS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --job-id|-j) JOB_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --part) PART="$2"; shift 2 ;;
    --require-success) REQUIRE_SUCCESS=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--job-id UUID] [--part bundle|json|manifest] [--require-success]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

export BASE_URL KORUS_USER KORUS_PASS CHAT_ID JOB_ID PART REQUIRE_SUCCESS
python3 <<'PY'
import json, os, sys, urllib.request

base = os.environ["BASE_URL"]
user = os.environ["KORUS_USER"]
pw = os.environ["KORUS_PASS"]
chat_id = os.environ["CHAT_ID"]
job_id = os.environ.get("JOB_ID", "")
part = os.environ.get("PART", "bundle")
require = os.environ.get("REQUIRE_SUCCESS", "false").lower() == "true"
ok = {"export_v1", "stub_written"}

login = urllib.request.Request(
    base + "/api/v1/auth/login",
    data=json.dumps({"username": user, "password": pw}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(login) as r:
    d = json.load(r)
tok = d.get("access_token") or d.get("accessToken")
if not tok:
    sys.exit("login failed")

def get(path):
    req = urllib.request.Request(base + path, headers={"Authorization": "Bearer " + tok})
    with urllib.request.urlopen(req) as r:
        return json.load(r)

if not job_id:
    print("GET latest export status ...", file=sys.stderr)
    latest = get(f"/api/v1/admin/chats/{chat_id}/export/latest/status")
    job_id = latest.get("job_id") or latest.get("jobId") or ""
    status = latest.get("status", "")
else:
    st = get(f"/api/v1/admin/chats/{chat_id}/export/{job_id}/status")
    status = st.get("status", "")

if not job_id:
    if require:
        sys.exit(f"No export job for chat {chat_id}")
    print("[SKIP] no export job", file=sys.stderr)
    sys.exit(0)

if status not in ok:
    if require:
        sys.exit(f"Job {job_id} status={status}")
    print(f"[SKIP] job {job_id} status={status}", file=sys.stderr)
    sys.exit(0)

path = f"/api/v1/admin/chats/{chat_id}/export/{job_id}/download?part={part}"
print(f"GET download part={part} job={job_id} ...", file=sys.stderr)
req = urllib.request.Request(base + path, headers={"Authorization": "Bearer " + tok})
with urllib.request.urlopen(req) as r:
    data = r.read()
print(f"[OK] download: {len(data)} bytes (status={status})", file=sys.stderr)
PY
