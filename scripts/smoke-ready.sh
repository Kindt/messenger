#!/usr/bin/env bash
# Stack readiness smoke (bash). Spec 003.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER="${SMOKE_USER:-csadmin}"
PASS="${SMOKE_PASS:-csadmin}"
STRICT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --strict) STRICT=true; shift ;;
    -h|--help) echo "Usage: $0 [--url URL] [--strict]"; exit 0 ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "[FAIL] $*" >&2; exit 1; }

echo "GET $BASE_URL/api/v1/health ..."
curl -fsS "$BASE_URL/api/v1/health" >/dev/null || fail "health"

echo "GET $BASE_URL/api/v1/health/ready ..."
ready=$(curl -fsS "$BASE_URL/api/v1/health/ready") || fail "health/ready"
echo "$ready" | grep -q '"database_ok"[[:space:]]*:[[:space:]]*true' || fail "database_ok=false"
if $STRICT; then
  echo "$ready" | grep -q '"redis_ok"[[:space:]]*:[[:space:]]*true' || fail "redis_ok"
  echo "$ready" | grep -q '"nats_ok"[[:space:]]*:[[:space:]]*true' || fail "nats_ok"
fi

echo "GET $BASE_URL/api/v1/media/capabilities ..."
cap=$(curl -fsS "$BASE_URL/api/v1/media/capabilities") || fail "capabilities"
echo "$cap" | grep -q max_upload_bytes || fail "capabilities body"

code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/admin/console")
[[ "$code" == "303" || "$code" == "302" ]] || fail "admin/console redirect expected 303, got $code"

login=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}") || fail "login"
token=$(python3 -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("access_token") or d.get("accessToken") or "")' "$login")
[[ -n "$token" ]] || fail "no token"

curl -fsS "$BASE_URL/admin/" >/dev/null || fail "/admin/ static"
curl -fsS -H "Authorization: Bearer $token" "$BASE_URL/api/v1/admin/ui/manifest" >/dev/null || fail "admin/ui/manifest"
sess=$(curl -fsS -H "Authorization: Bearer $token" "$BASE_URL/api/v1/admin/session") || fail "admin/session"
echo "$sess" | grep -q user_id || fail "admin session missing user_id"

echo "[OK] smoke-ready: health, ready, capabilities, admin, JWT session"
