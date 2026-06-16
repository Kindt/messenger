#!/bin/sh
# Enable OIDC identity provider in realm avandocmsg (P2-2 SSO playbook).
# Requires: KEYCLOAK_URL, KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD,
#   SSO_IDP_ALIAS, SSO_CLIENT_ID, SSO_CLIENT_SECRET, SSO_DISCOVERY_URL
set -eu

KC_BASE="${KEYCLOAK_URL:-http://127.0.0.1:8080}"
MASTER_USER="${KEYCLOAK_ADMIN:-admin}"
MASTER_PASS="${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD required}"
REALM="${KEYCLOAK_REALM:-avandocmsg}"
ALIAS="${SSO_IDP_ALIAS:?SSO_IDP_ALIAS required}"
CLIENT_ID="${SSO_CLIENT_ID:?SSO_CLIENT_ID required}"
CLIENT_SECRET="${SSO_CLIENT_SECRET:?SSO_CLIENT_SECRET required}"
DISCOVERY="${SSO_DISCOVERY_URL:?SSO_DISCOVERY_URL required}"

token() {
  curl -fsS -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli&username=$MASTER_USER&password=$MASTER_PASS&grant_type=password" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

echo "=== keycloak-enable-identity-provider alias=$ALIAS realm=$REALM ==="
TOK=$(token) || { echo "[FAIL] master token"; exit 1; }

BODY=$(cat <<EOF
{
  "alias": "$ALIAS",
  "displayName": "Corporate SSO",
  "providerId": "oidc",
  "enabled": true,
  "trustEmail": true,
  "storeToken": false,
  "firstBrokerLoginFlowAlias": "first broker login",
  "config": {
    "clientId": "$CLIENT_ID",
    "clientSecret": "$CLIENT_SECRET",
    "discoveryUrl": "$DISCOVERY",
    "clientAuthMethod": "client_secret_post",
    "defaultScope": "openid profile email",
    "syncMode": "IMPORT",
    "useJwksUrl": "true"
  }
}
EOF
)

code=$(curl -sS -o /tmp/kc-idp.out -w "%{http_code}" -X POST \
  "$KC_BASE/admin/realms/$REALM/identity-provider/instances" \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  -d "$BODY")

if [ "$code" = "409" ]; then
  code=$(curl -sS -o /tmp/kc-idp.out -w "%{http_code}" -X PUT \
    "$KC_BASE/admin/realms/$REALM/identity-provider/instances/$ALIAS" \
    -H "Authorization: Bearer $TOK" \
    -H "Content-Type: application/json" \
    -d "$BODY")
fi

if [ "$code" != "201" ] && [ "$code" != "204" ] && [ "$code" != "200" ]; then
  echo "[FAIL] IdP upsert HTTP $code"
  cat /tmp/kc-idp.out
  exit 1
fi

echo "[OK] Identity provider $ALIAS enabled in realm $REALM"
echo "Next: configure messenger-web redirect URIs and test browser login."
