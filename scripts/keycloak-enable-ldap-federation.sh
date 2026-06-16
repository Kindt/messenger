#!/bin/sh
# Enable LDAP/AD user federation in realm avandocmsg (P2-3).
# Requires: KEYCLOAK_URL, KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD,
#   LDAP_FEDERATION_NAME, LDAP_CONNECTION_URL, LDAP_USERS_DN,
#   LDAP_BIND_DN, LDAP_BIND_PASSWORD
set -eu

KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8080}"
MASTER_USER="${KEYCLOAK_ADMIN:-admin}"
MASTER_PASS="${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD required}"
REALM="${KEYCLOAK_REALM:-avandocmsg}"
NAME="${LDAP_FEDERATION_NAME:?LDAP_FEDERATION_NAME required}"
CONN_URL="${LDAP_CONNECTION_URL:?LDAP_CONNECTION_URL required}"
USERS_DN="${LDAP_USERS_DN:?LDAP_USERS_DN required}"
BIND_DN="${LDAP_BIND_DN:?LDAP_BIND_DN required}"
BIND_PASS="${LDAP_BIND_PASSWORD:?LDAP_BIND_PASSWORD required}"
VENDOR="${LDAP_VENDOR:-ad}"
EDIT_MODE="${LDAP_EDIT_MODE:-READ_ONLY}"

token() {
  curl -fsS -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli&username=$MASTER_USER&password=$MASTER_PASS&grant_type=password" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

echo "=== keycloak-enable-ldap-federation name=$NAME realm=$REALM ==="
TOK=$(token) || { echo "[FAIL] master token"; exit 1; }

BODY=$(cat <<EOF
{
  "name": "$NAME",
  "providerId": "ldap",
  "providerType": "org.keycloak.storage.UserStorageProvider",
  "config": {
    "enabled": ["true"],
    "priority": ["0"],
    "editMode": ["$EDIT_MODE"],
    "syncRegistrations": ["false"],
    "vendor": ["$VENDOR"],
    "usernameLDAPAttribute": ["sAMAccountName"],
    "rdnLDAPAttribute": ["cn"],
    "uuidLDAPAttribute": ["objectGUID"],
    "userObjectClasses": ["person, organizationalPerson, user"],
    "connectionUrl": ["$CONN_URL"],
    "usersDn": ["$USERS_DN"],
    "bindDn": ["$BIND_DN"],
    "bindCredential": ["$BIND_PASS"],
    "searchScope": ["2"],
    "useTruststoreSpi": ["ldapsOnly"],
    "pagination": ["true"]
  }
}
EOF
)

code=$(curl -sS -o /tmp/kc-ldap.out -w "%{http_code}" -X POST \
  "$KC_BASE/admin/realms/$REALM/user-storage" \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  -d "$BODY")

if [ "$code" = "409" ]; then
  existing_id=$(curl -fsS \
    "$KC_BASE/admin/realms/$REALM/user-storage" \
    -H "Authorization: Bearer $TOK" \
    | sed -n "s/.*\"id\":\"\\([^\"]*\\)\".*\"name\":\"$NAME\".*/\\1/p" | head -1)
  if [ -z "$existing_id" ]; then
    echo "[FAIL] LDAP provider exists but id not found for name=$NAME"
    cat /tmp/kc-ldap.out
    exit 1
  fi
  code=$(curl -sS -o /tmp/kc-ldap.out -w "%{http_code}" -X PUT \
    "$KC_BASE/admin/realms/$REALM/user-storage/$existing_id" \
    -H "Authorization: Bearer $TOK" \
    -H "Content-Type: application/json" \
    -d "$BODY")
fi

if [ "$code" != "201" ] && [ "$code" != "204" ] && [ "$code" != "200" ]; then
  echo "[FAIL] LDAP federation upsert HTTP $code"
  cat /tmp/kc-ldap.out
  exit 1
fi

echo "[OK] LDAP user federation $NAME enabled in realm $REALM"
echo "Next: Keycloak Admin -> User federation -> Sync users; assign admin role manually (FR-INT-03)."
