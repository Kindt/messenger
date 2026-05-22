#!/usr/bin/env bash
# Admin export attachments + manifest + json inspect.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
JOB_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
REQUIRE_SUCCESS=false
MIN_JSON_BYTES="${MIN_JSON_BYTES:-32}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --job-id|-j) JOB_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --require-success) REQUIRE_SUCCESS=true; shift ;;
    -h|--help)
      echo "Usage: $0 --chat-id UUID [--job-id UUID] [--require-success]"
      exit 0
      ;;
    *) fail "Unknown: $1" ;;
  esac
done

[[ -n "$CHAT_ID" ]] || fail "Missing --chat-id"
command -v python3 >/dev/null 2>&1 || fail "python3 required"

export BASE_URL KORUS_USER KORUS_PASS CHAT_ID JOB_ID REQUIRE_SUCCESS MIN_JSON_BYTES
python3 <<'PY'
import json, os, sys, urllib.request

base = os.environ["BASE_URL"]
user = os.environ["KORUS_USER"]
pw = os.environ["KORUS_PASS"]
chat_id = os.environ["CHAT_ID"]
job_id = os.environ.get("JOB_ID", "")
require = os.environ.get("REQUIRE_SUCCESS", "false").lower() == "true"
min_json = int(os.environ.get("MIN_JSON_BYTES", "32"))
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
        return r.read(), r.headers.get("Content-Type", "")

def get_json(path):
    raw, _ = get(path)
    return json.loads(raw.decode())

if not job_id:
    print("GET latest export status ...", file=sys.stderr)
    latest = get_json(f"/api/v1/admin/chats/{chat_id}/export/latest/status")
    job_id = latest.get("job_id") or latest.get("jobId") or ""
    status = latest.get("status", "")
else:
    st = get_json(f"/api/v1/admin/chats/{chat_id}/export/{job_id}/status")
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

root = f"/api/v1/admin/chats/{chat_id}/export/{job_id}"
print("GET attachments ...", file=sys.stderr)
att = get_json(f"{root}/attachments?limit=100")
if not (att.get("zip_bundle") or att.get("zipBundle")):
    sys.exit("attachments: expected zip_bundle=true")
total = att.get("total_count", att.get("totalCount", 0))
fc = att.get("file_count", att.get("fileCount", 0))
print(f"[OK] attachments zip_bundle=true total={total} page={fc}", file=sys.stderr)

print("GET download?part=manifest ...", file=sys.stderr)
manifest = get_json(f"{root}/download?part=manifest")
files = manifest.get("files") or []
print(f"[OK] manifest entries={len(files)}", file=sys.stderr)

file_id = ""
if files:
    f0 = files[0]
    file_id = f0.get("file_id") or f0.get("fileId") or ""
if not file_id:
    att_files = att.get("files") or []
    if att_files:
        file_id = att_files[0].get("file_id") or att_files[0].get("fileId") or ""
if file_id or fc > 0:
    if not file_id:
        sys.exit("attachments: file_count>0 but no file_id in manifest")
    print(f"GET download?part=binary file_id={file_id} ...", file=sys.stderr)
    raw_bin, _ = get(f"{root}/download?part=binary&file_id={file_id}")
    if len(raw_bin) < 8:
        sys.exit(f"binary part too small ({len(raw_bin)} bytes)")
    print(f"[OK] binary part {len(raw_bin)} bytes", file=sys.stderr)

print("GET download?part=json ...", file=sys.stderr)
raw, _ = get(f"{root}/download?part=json")
if len(raw) < min_json:
    sys.exit(f"json part too small ({len(raw)} bytes)")
print(f"[OK] json part {len(raw)} bytes", file=sys.stderr)
print(f"[OK] export inspect complete (job {job_id})", file=sys.stderr)
PY
