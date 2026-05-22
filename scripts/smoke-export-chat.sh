#!/usr/bin/env bash
# Smoke: POST chat export -> poll GET status -> download bundle/json/manifest.
# Requires export-replay-worker + DB; download needs EXPORT_DIR and/or MinIO on core-api.
# Usage: ./scripts/smoke-export-chat.sh [--url URL] [--user U] [--pass P] [--chat-id UUID] [--skip-download]
set -euo pipefail

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

BASE_URL="${BASE_URL:-http://localhost:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"
CHAT_ID=""
JOB_ID=""
POLL_SECONDS="${POLL_SECONDS:-90}"
POLL_INTERVAL_SEC="${POLL_INTERVAL_SEC:-2}"
SKIP_DOWNLOAD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u)
      [[ $# -ge 2 ]] || fail "--url requires a value"
      BASE_URL="$2"
      shift 2
      ;;
    --user) KORUS_USER="$2"; shift 2 ;;
    --pass) KORUS_PASS="$2"; shift 2 ;;
    --chat-id|-c) CHAT_ID="$2"; shift 2 ;;
    --job-id|-j) JOB_ID="$2"; shift 2 ;;
    --poll-seconds) POLL_SECONDS="$2"; shift 2 ;;
    --skip-download) SKIP_DOWNLOAD=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--url URL] [--user U] [--pass P] [--chat-id UUID] [--skip-download]"
      echo "Env: BASE_URL, KORUS_USER, KORUS_PASS, POLL_SECONDS, POLL_INTERVAL_SEC"
      exit 0
      ;;
    *)
      fail "Unknown option: $1 (try --help)"
      ;;
  esac
done

json_get() {
  local key="$1"
  python3 -c '
import json, sys
key = sys.argv[1]
data = json.load(sys.stdin)
parts = key.split("_")
camel = parts[0] + "".join(p.capitalize() for p in parts[1:])
for n in (key, camel):
    if n in data and data[n] is not None:
        print(data[n])
        break
else:
    print("")
' "$key"
}

login_uri="${BASE_URL}/api/v1/auth/login"
echo "POST $login_uri (user=$KORUS_USER)..." >&2
login_body=$(printf '{"username":"%s","password":"%s"}' "$KORUS_USER" "$KORUS_PASS")
login_resp=$(curl -fsS -X POST "$login_uri" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "$login_body") || fail "login"
token=$(echo "$login_resp" | json_get access_token)
[[ -n "$token" ]] || fail "no access token from login"
auth_hdr=(-H "Authorization: Bearer $token")

if [[ -z "$CHAT_ID" ]]; then
  echo "GET ${BASE_URL}/api/v1/chats ..." >&2
  chats_resp=$(curl -fsS "${BASE_URL}/api/v1/chats" "${auth_hdr[@]}") || fail "list chats"
  CHAT_ID=$(echo "$chats_resp" | python3 -c "
import json, sys
chats = json.load(sys.stdin)
if not chats:
    sys.exit(1)
c = chats[0]
print(c.get('id') or c.get('chat_id') or '')
" 2>/dev/null) || fail "no chats for user; pass --chat-id"
  echo "Using first chat: $CHAT_ID" >&2
fi

export_uri="${BASE_URL}/api/v1/chats/${CHAT_ID}/export"
if [[ -n "$JOB_ID" ]]; then
  job_id="$JOB_ID"
  echo "Using existing job_id=$job_id (skip POST)" >&2
else
  echo "POST $export_uri ..." >&2
  accept_code=$(curl -sS -o /tmp/korus_export_accept.json -w "%{http_code}" \
    -X POST "$export_uri" "${auth_hdr[@]}" -H "Content-Type: application/json" -d "{}") || fail "export POST"
  [[ "$accept_code" == "202" ]] || fail "expected 202 Accepted, got $accept_code"
  accept_body=$(cat /tmp/korus_export_accept.json)
  job_id=$(echo "$accept_body" | json_get job_id)
  [[ -n "$job_id" ]] || fail "no job_id in response"
  echo "[OK] job_id=$job_id" >&2
fi

status_uri="${export_uri}/${job_id}"
deadline=$(( $(date +%s) + POLL_SECONDS ))
terminal_re='^(export_v1|stub_written|export_failed)$'

download_part() {
  local uri="$1" label="$2"
  echo "GET $uri ($label) ..." >&2
  if code=$(curl -sS -o /tmp/korus_export_dl.bin -w "%{http_code}" "$uri" "${auth_hdr[@]}"); then
    if [[ "$code" == "200" ]]; then
      bytes=$(wc -c < /tmp/korus_export_dl.bin | tr -d ' ')
      echo "[OK] $label : $bytes bytes" >&2
      return 0
    fi
  fi
  echo "[WARN] $label failed" >&2
  return 1
}

while [[ $(date +%s) -lt $deadline ]]; do
  sleep "$POLL_INTERVAL_SEC"
  st_resp=$(curl -fsS "$status_uri" "${auth_hdr[@]}") || fail "poll status"
  status=$(echo "$st_resp" | json_get status)
  echo "  status=$status" >&2
  if [[ "$status" =~ $terminal_re ]]; then
    echo "[OK] Export finished: $status" >&2
    output_format=$(echo "$st_resp" | json_get output_format)
    output_path=$(echo "$st_resp" | json_get output_path)
    [[ -n "$output_path" ]] && echo "  output_path=$output_path" >&2
    [[ -n "$output_format" ]] && echo "  output_format=$output_format" >&2

    if ! $SKIP_DOWNLOAD; then
      dl_base="${status_uri}/download"
      is_zip=false
      [[ "$output_format" == "zip" ]] && is_zip=true
      [[ "$output_path" == *".export.zip" ]] && is_zip=true
      download_part "$dl_base" "bundle" || true
      if $is_zip; then
        download_part "${dl_base}?part=json" "json" || true
        if att=$(curl -fsS "${status_uri}/attachments" "${auth_hdr[@]}" 2>/dev/null); then
          fc=$(echo "$att" | json_get file_count)
          zb=$(echo "$att" | json_get zip_bundle)
          echo "[OK] attachments list: ${fc:-0} file(s) zip_bundle=${zb:-?}" >&2
        fi
        manifest=$(curl -fsS "${dl_base}?part=manifest" "${auth_hdr[@]}") || true
        download_part "${dl_base}?part=manifest" "manifest" || true
        if [[ -n "${manifest:-}" ]]; then
          ids=$(echo "$manifest" | python3 -c "
import json, sys
d = json.load(sys.stdin)
files = d.get('files') or []
ids = [f.get('fileId') for f in files if f.get('fileId')][:2]
print(','.join(ids))
" 2>/dev/null || true)
          if [[ -n "$ids" ]]; then
            first="${ids%%,*}"
            download_part "${dl_base}?part=binary&file_id=${first}" "binary" || true
            if [[ "$ids" == *,* ]]; then
              download_part "${dl_base}?part=binaries&file_ids=${ids}" "binaries" || true
            fi
          fi
        fi
      else
        download_part "${dl_base}?part=json" "json" || true
      fi
    else
      echo "Skipped download (--skip-download)" >&2
    fi
    exit 0
  fi
done

fail "timed out after ${POLL_SECONDS}s waiting for export job $job_id"
