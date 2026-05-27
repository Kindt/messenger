#!/usr/bin/env bash
# Ensure smoke test users: register via API + Keycloak email/password fix.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
MASTER_USER="${KEYCLOAK_ADMIN:-admin}"
MASTER_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
PASS="${SMOKE_USER_PASS:-smokepass123}"
REALM=avandocmsg

USERS=(smoke_user_a smoke_user_b smoke_user_c)
EMAILS=(smoke_a@korus.local smoke_b@korus.local smoke_c@korus.local)
NAMES=("Smoke User A" "Smoke User B" "Smoke User C")

kc_token() {
  curl -fsS -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli&username=$MASTER_USER&password=$MASTER_PASS&grant_type=password" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

kc_fix_user() {
  local user="$1" email="$2" first="$3" last="$4" pass="$5" tok="$6"
  local id
  id=$(curl -fsS "$KC_BASE/admin/realms/$REALM/users?username=$user" \
    -H "Authorization: Bearer $tok" \
    | python3 -c 'import json,sys; u=json.load(sys.stdin); print(u[0]["id"] if u else "")')
  if [[ -z "$id" ]]; then
    echo "keycloak-ensure-smoke: user $user not in Keycloak yet, skip KC fix"
    return 0
  fi
  curl -fsS -X PUT "$KC_BASE/admin/realms/$REALM/users/$id" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"emailVerified\":true,\"firstName\":\"$first\",\"lastName\":\"$last\",\"enabled\":true}" >/dev/null
  curl -fsS -X PUT "$KC_BASE/admin/realms/$REALM/users/$id/reset-password" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"$pass\",\"temporary\":false}" >/dev/null
  echo "keycloak-ensure-smoke: KC ok $user"
}

echo "=== keycloak-ensure-smoke-users ($BASE_URL) ==="

for i in "${!USERS[@]}"; do
  u="${USERS[$i]}"
  smoke_register "$BASE_URL" "$u" "$PASS" "${NAMES[$i]}" || smoke_fail "register $u failed"
  smoke_login "$BASE_URL" "$u" "$PASS" >/dev/null || smoke_fail "login $u failed"
  echo "keycloak-ensure-smoke: API ok $u"
done

if TOK=$(kc_token 2>/dev/null); then
  kc_fix_user smoke_user_a "${EMAILS[0]}" "Smoke" "UserA" "$PASS" "$TOK"
  kc_fix_user smoke_user_b "${EMAILS[1]}" "Smoke" "UserB" "$PASS" "$TOK"
  kc_fix_user smoke_user_c "${EMAILS[2]}" "Smoke" "UserC" "$PASS" "$TOK"
else
  echo "keycloak-ensure-smoke: WARN cannot get KC master token, skipping email fix" >&2
fi

bash "$SCRIPT_DIR/keycloak-ensure-dev-users.sh" 2>/dev/null || true
echo "=== done ==="
