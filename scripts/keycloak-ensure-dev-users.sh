#!/bin/sh
# Fix/create dev users in realm avandocmsg (Keycloak 24+ requires email for password grant).
# Waits for Keycloak realm import — CI often races compose "started" vs realm ready.
set -eu

KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
MASTER_USER="${KEYCLOAK_ADMIN:-admin}"
MASTER_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM=avandocmsg
WAIT_SEC="${KEYCLOAK_ENSURE_WAIT_SEC:-180}"

token() {
  curl -fsS -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli&username=$MASTER_USER&password=$MASTER_PASS&grant_type=password" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

kc_put_ok() {
  code="$1"
  case "$code" in
    200|201|204|409) return 0 ;;
    *) return 1 ;;
  esac
}

wait_keycloak_ready() {
  echo "keycloak-ensure: waiting up to ${WAIT_SEC}s for realm $REALM ..."
  i=0
  while [ "$i" -lt "$WAIT_SEC" ]; do
    if curl -fsS --max-time 5 "$KC_BASE/realms/$REALM/.well-known/openid-configuration" >/dev/null 2>&1; then
      if TOK=$(token 2>/dev/null) && [ -n "$TOK" ]; then
        echo "keycloak-ensure: realm + master token ready (${i}s)"
        return 0
      fi
    fi
    i=$((i + 5))
    sleep 5
  done
  echo "keycloak-ensure: timed out waiting for Keycloak realm $REALM" >&2
  return 1
}

create_user_if_missing() {
  user="$1"
  email="$2"
  first="$3"
  last="$4"
  pass="$5"
  tok="$6"
  id=$(curl -fsS "$KC_BASE/admin/realms/$REALM/users?username=$user" \
    -H "Authorization: Bearer $tok" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
  if [ -n "$id" ]; then
    echo "$id"
    return 0
  fi
  echo "keycloak-ensure: creating user $user" >&2
  code=$(curl -sS -o /tmp/kc-create-user.json -w "%{http_code}" -X POST "$KC_BASE/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"email\":\"$email\",\"emailVerified\":true,\"firstName\":\"$first\",\"lastName\":\"$last\",\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"$pass\",\"temporary\":false}]}")
  if ! kc_put_ok "$code"; then
    echo "keycloak-ensure: create user $user failed http $code $(cat /tmp/kc-create-user.json 2>/dev/null || true)" >&2
    return 1
  fi
  id=$(curl -fsS "$KC_BASE/admin/realms/$REALM/users?username=$user" \
    -H "Authorization: Bearer $tok" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
  if [ -z "$id" ]; then
    echo "keycloak-ensure: user $user still missing after create" >&2
    return 1
  fi
  echo "$id"
}

fix_user() {
  user="$1"
  email="$2"
  first="$3"
  last="$4"
  pass="$5"
  tok="$6"
  id=$(create_user_if_missing "$user" "$email" "$first" "$last" "$pass" "$tok") || return 1
  code=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT "$KC_BASE/admin/realms/$REALM/users/$id" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"emailVerified\":true,\"firstName\":\"$first\",\"lastName\":\"$last\",\"enabled\":true}")
  if ! kc_put_ok "$code"; then
    echo "keycloak-ensure: update user $user failed http $code"
    return 1
  fi
  code=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT "$KC_BASE/admin/realms/$REALM/users/$id/reset-password" \
    -H "Authorization: Bearer $tok" -H "Content-Type: application/json" \
    -d "{\"type\":\"password\",\"value\":\"$pass\",\"temporary\":false}")
  if ! kc_put_ok "$code"; then
    echo "keycloak-ensure: reset-password $user failed http $code"
    return 1
  fi
  echo "keycloak-ensure: ok $user ($email)"
}

echo "=== keycloak-ensure-dev-users ($KC_BASE) ==="
wait_keycloak_ready || exit 1
TOK=$(token) || { echo "keycloak-ensure: cannot get master token"; exit 1; }
fix_user admin admin@korus.local System Admin admin "$TOK"
fix_user csadmin csadmin@korus.local Console Superuser csadmin "$TOK"
echo "=== done ==="
bash "$(dirname "$0")/keycloak-verify-realm.sh"
