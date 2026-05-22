#!/usr/bin/env bash
# shellcheck disable=SC2034
# Dot-source: verify_export_cancel_audit BASE_URL TOKEN JOB_ID ACTION
verify_export_cancel_audit() {
  local base_url="$1" token="$2" job_id="$3" action="$4"
  local skip="${SMOKE_SKIP_AUDIT:-false}"
  if [[ "$skip" == "true" || "$skip" == "1" ]]; then
    echo "  audit check skipped" >&2
    return 0
  fi
  local qs="limit=10&action=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$action")"
  qs+="&resource_type=export_job"
  qs+="&resource_id=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$job_id")"
  local body
  body=$(curl -fsS "${base_url}/api/v1/admin/audit-events?${qs}" -H "Authorization: Bearer ${token}") \
    || { echo "[FAIL] audit GET" >&2; return 1; }
  local count
  count=$(echo "$body" | python3 -c "
import json, sys
action, job_id = sys.argv[1], sys.argv[2]
d = json.load(sys.stdin)
rows = d if isinstance(d, list) else []
hits = [r for r in rows if r.get('action') == action and r.get('resource_id') == job_id and r.get('resource_type') == 'export_job']
print(len(hits))
if not hits:
  sys.exit(2)
" "$action" "$job_id") || {
    echo "[FAIL] expected audit $action resource_id=$job_id" >&2
    return 1
  }
  echo "[OK] audit $action ($count row(s))" >&2
}

verify_export_requested_audit() {
  verify_export_cancel_audit "$1" "$2" "$3" "export.requested"
}
