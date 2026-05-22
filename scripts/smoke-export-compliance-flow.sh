#!/usr/bin/env bash
# Admin export-compliance-prep -> export-suggest -> export (if needed) -> poll.
set -euo pipefail

fail() { echo "[FAIL] $*" >&2; exit 1; }

CHAT_ID=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
DISPATCH="${DISPATCH:-local}"
POLL_SECONDS="${POLL_SECONDS:-120}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-2}"
SKIP_PREP=false
INCLUDE_FILE=false
FILE_NAME="compliance-smoke.txt"
SKIP_DOWNLOAD=false
SKIP_INSPECT=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --dispatch) DISPATCH="$2"; shift 2 ;;
    --poll-seconds) POLL_SECONDS="$2"; shift 2 ;;
    --skip-prep) SKIP_PREP=true; shift ;;
    --include-file) INCLUDE_FILE=true; shift ;;
    --file-name) FILE_NAME="$2"; shift 2 ;;
    --skip-download) SKIP_DOWNLOAD=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--chat-id UUID] [--dispatch local|nats|both] [--poll-seconds N] [--skip-prep] [--skip-download]"
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

TOK="$(token)"
[[ -n "$TOK" ]] || fail "login failed"

FLOW_ENV="$(mktemp)"
trap 'rm -f "$FLOW_ENV"' EXIT
export BASE_URL TOK KORUS_USER KORUS_PASS CHAT_ID DISPATCH POLL_SECONDS POLL_INTERVAL_SEC SKIP_PREP INCLUDE_FILE FILE_NAME FLOW_ENV SCRIPT_DIR
python3 <<'PY'
import json, os, sys, time, urllib.error, urllib.request

base = os.environ["BASE_URL"]
tok = os.environ["TOK"]
chat_id = os.environ.get("CHAT_ID", "")
dispatch = os.environ.get("DISPATCH", "local")
poll_sec = int(os.environ.get("POLL_SECONDS", "120"))
poll_iv = int(os.environ.get("POLL_INTERVAL_SEC", "2"))
skip_prep = os.environ.get("SKIP_PREP", "false").lower() == "true"
script_dir = os.environ["SCRIPT_DIR"]
terminal = {"export_v1", "stub_written", "export_failed", "export_cancelled"}

def req(method, path, body=None):
    h = {"Authorization": "Bearer " + tok, "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(base + path, data=data, headers=h, method=method)
    with urllib.request.urlopen(r) as resp:
        return json.load(resp)

if not chat_id and not skip_prep:
    import subprocess
    prep_cmd = [f"{script_dir}/smoke-admin-export-compliance-prep.sh", "--url", base]
    if os.environ.get("INCLUDE_FILE", "false").lower() == "true":
        prep_cmd.append("--include-file")
        file_name = os.environ.get("FILE_NAME", "")
        if file_name:
            prep_cmd.extend(["--file-name", file_name])
    out = subprocess.check_output(prep_cmd, text=True)
    for line in out.splitlines():
        if line.startswith("CHAT_ID="):
            chat_id = line.split("=", 1)[1].strip()
    if not chat_id:
        sys.exit("prep did not return chat_id")
    time.sleep(2)

if not chat_id:
    sys.exit("chat_id required")

print(f"Using chat {chat_id}", file=sys.stderr)
print(f"POST export-suggest ({dispatch}) ...", file=sys.stderr)
suggest = req("POST", f"/api/v1/admin/chats/{chat_id}/export-suggest", {
    "dispatch": dispatch,
    "candidate_message_count": 3,
    "reason": "hot_body_candidates",
})
job_id = suggest.get("auto_queued_job_id") or suggest.get("autoQueuedJobId")
if not job_id:
    print("POST admin export ...", file=sys.stderr)
    acc = req("POST", f"/api/v1/admin/chats/{chat_id}/export", {})
    job_id = acc.get("job_id") or acc.get("jobId")
if not job_id:
    sys.exit("no job_id")

print(f"[OK] job_id={job_id}", file=sys.stderr)
deadline = time.time() + poll_sec
final = None
while time.time() < deadline:
    final = req("GET", f"/api/v1/admin/chats/{chat_id}/export/{job_id}/status")
    st = final.get("status", "")
    print(f"  status={st}", file=sys.stderr)
    if st in terminal:
        break
    time.sleep(poll_iv)

if not final or final.get("status") not in terminal:
    sys.exit(f"poll timeout (last={final and final.get('status')})")
if final.get("status") == "export_failed":
    sys.exit("export_failed")
status = final.get("status", "")
with open(os.environ["FLOW_ENV"], "w", encoding="utf-8") as f:
    f.write(f"FLOW_CHAT_ID={chat_id}\n")
    f.write(f"FLOW_JOB_ID={job_id}\n")
    f.write(f"FLOW_STATUS={status}\n")
print(f"[OK] compliance flow finished: {status}", file=sys.stderr)
PY

# shellcheck disable=SC1090
source "$FLOW_ENV"
if [[ "$FLOW_STATUS" == "export_v1" || "$FLOW_STATUS" == "stub_written" ]]; then
  if ! $SKIP_DOWNLOAD; then
    "${SCRIPT_DIR}/smoke-admin-export-download.sh" --chat-id "$FLOW_CHAT_ID" --job-id "$FLOW_JOB_ID" \
      --url "$BASE_URL" --require-success
  fi
  if ! $SKIP_INSPECT; then
    "${SCRIPT_DIR}/smoke-admin-export-inspect.sh" --chat-id "$FLOW_CHAT_ID" --job-id "$FLOW_JOB_ID" \
      --url "$BASE_URL" --require-success
  fi
fi
