#!/usr/bin/env bash
# CI gate: Keycloak realm import + csadmin API login must work before admin smokes.
set -euo pipefail

fail() { echo "[KEYCLOAK-VERIFY FAIL] $*" >&2; exit 1; }

KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
KORUS_USER="${KORUS_USER:-csadmin}"
KORUS_PASS="${KORUS_PASS:-csadmin}"

echo "=== keycloak-verify-realm ($KC_BASE -> $BASE_URL) ==="

curl -fsS --max-time 10 "$KC_BASE/realms/avandocmsg/.well-known/openid-configuration" >/dev/null \
  || fail "realm avandocmsg not reachable on $KC_BASE (Keycloak import crashed?)"

if command -v docker >/dev/null 2>&1; then
  if docker ps -a --format '{{.Names}} {{.Status}}' 2>/dev/null | grep -Ei 'keycloak' | grep -Ei 'exited|dead' >/dev/null; then
    docker ps -a --format '{{.Names}} {{.Status}}' 2>/dev/null | grep -i keycloak >&2 || true
    fail "keycloak container not running"
  fi
fi

python3 -c '
import json, sys, urllib.error, urllib.request
base, user, pw = sys.argv[1], sys.argv[2], sys.argv[3]
req = urllib.request.Request(
  base + "/api/v1/auth/login",
  data=json.dumps({"username": user, "password": pw}).encode(),
  headers={"Content-Type": "application/json"},
  method="POST",
)
try:
  with urllib.request.urlopen(req, timeout=15) as r:
    d = json.load(r)
except urllib.error.HTTPError as e:
  sys.exit(f"login HTTP {e.code}")
tok = d.get("access_token") or d.get("accessToken") or ""
if not tok:
  sys.exit("login returned no access_token")
print("[OK] csadmin login")
' "$BASE_URL" "$KORUS_USER" "$KORUS_PASS" || fail "csadmin login via API failed"

echo "=== keycloak-verify-realm OK ==="
