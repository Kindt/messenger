#!/bin/sh
# Fix dev users in realm avandocmsg (Keycloak 24+ requires email for password grant).
set -eu

KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
MASTER_USER="${KEYCLOAK_ADMIN:-admin}"
MASTER_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM=avandocmsg

token() {
  curl -fsS -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli&username=$MASTER_USER&password=$MASTER_PASS&grant_type=password" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

fix_user() {
  user="$1"
  email="$2"
  first="$3"
  last="$4"
  pass="$5"
  tok="$6"
  id=$(curl -fsS "$KC_BASE/admin/realms/$REALM/users?username=$user" \
    -H "Authorization: Bearer $tok" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
  if [ -z "$id" ]; then
    echo "keycloak-ensure: user $user not found, skip"
    return 0
  fi
  curl -fsS -X PUT "$KC_BASE/admin/realms/$REALM/users/$id" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"emailVerified\":true,\"firstName\":\"$first\",\"lastName\":\"$last\",\"enabled\":true}" >/dev/null
  curl -fsS -X PUT "$KC_BASE/admin/realms/$REALM/users/$id/reset-password" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"$pass\",\"temporary\":false}" >/dev/null
  echo "keycloak-ensure: ok $user ($email)"
}

echo "=== keycloak-ensure-dev-users ($KC_BASE) ==="
TOK=$(token) || { echo "keycloak-ensure: cannot get master token"; exit 1; }
fix_user admin admin@korus.local System Admin admin "$TOK"
fix_user csadmin csadmin@korus.local Console Superuser csadmin "$TOK"
echo "=== done ==="
