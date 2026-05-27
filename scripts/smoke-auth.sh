#!/usr/bin/env bash
# Auth smoke (bash). Spec 003.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER="${SMOKE_USER:-csadmin}"
PASS="${SMOKE_PASS:-csadmin}"
SKIP_REFRESH=false
SKIP_LOGOUT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --skip-refresh) SKIP_REFRESH=true; shift ;;
    --skip-logout) SKIP_LOGOUT=true; shift ;;
    -h|--help) echo "Usage: $0 [--url URL] [--skip-refresh] [--skip-logout]"; exit 0 ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "[FAIL] $*" >&2; exit 1; }

code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/admin/console")
[[ "$code" == "303" || "$code" == "302" ]] || fail "admin/console redirect"

login=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}") || fail "login"
read -r token rt <<< "$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print((d.get("access_token") or d.get("accessToken") or ""), (d.get("refresh_token") or d.get("refreshToken") or ""))' "$login")"
[[ -n "$token" ]] || fail "no access token"

if ! $SKIP_REFRESH && [[ -n "$rt" ]]; then
  ref=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/refresh" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"refresh_token\":\"$rt\"}" 2>/dev/null) || ref=""
  if [[ -n "$ref" ]]; then
    token=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("access_token") or d.get("accessToken") or sys.argv[2])' "$ref" "$token")
    rt=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("refresh_token") or d.get("refreshToken") or sys.argv[2])' "$ref" "$rt")
  fi
fi

curl -fsS -H "Authorization: Bearer $token" "$BASE_URL/api/v1/admin/ui/manifest" >/dev/null || fail "admin/ui/manifest"
curl -fsS -H "Authorization: Bearer $token" "$BASE_URL/api/v1/admin/ui/stats" >/dev/null || fail "admin/ui/stats"

if ! $SKIP_LOGOUT && [[ -n "$rt" ]]; then
  code=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/auth/logout" \
    -H "Content-Type: application/json; charset=utf-8" \
    -d "{\"refresh_token\":\"$rt\"}")
  [[ "$code" == "204" ]] || fail "logout expected 204, got $code"
fi

curl -fsS -H "Authorization: Bearer $token" "$BASE_URL/api/v1/admin/session" >/dev/null || fail "admin/session"
echo "[OK] smoke-auth"
