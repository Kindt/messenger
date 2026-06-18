#!/bin/sh
# Smoke: login-options public API (LDAP stack optional).
set -eu

API="${KORUS_API_URL:-http://127.0.0.1:18080}"
URL="$API/api/v1/auth/login-options"

echo "=== smoke-ldap-auth GET $URL ==="
code=$(curl -sS -o /tmp/korus-login-options.json -w "%{http_code}" "$URL")
if [ "$code" != "200" ]; then
  echo "[FAIL] login-options HTTP $code"
  cat /tmp/korus-login-options.json
  exit 1
fi
echo "[OK] login-options HTTP $code"
cat /tmp/korus-login-options.json
echo ""

if [ "${SMOKE_LDAP_APPLY:-0}" = "1" ]; then
  echo "=== optional LDAP apply via admin (requires admin token) ==="
  if [ -z "${KORUS_ADMIN_TOKEN:-}" ]; then
    echo "[SKIP] KORUS_ADMIN_TOKEN not set"
    exit 0
  fi
  ORG_ID="${KORUS_ORG_ID:?KORUS_ORG_ID required when SMOKE_LDAP_APPLY=1}"
  BODY='{"allow_local_password":true,"providers":[{"id":"dev-ldap","type":"ldap","alias":"dev-ldap","display_name":"Dev LDAP","priority":0,"enabled":true,"settings":{"vendor":"other","connection_url":"ldap://openldap:389","users_dn":"dc=korus,dc=local","bind_dn":"cn=admin,dc=korus,dc=local","bind_password":"admin"}}],"apply_to_keycloak":true}'
  code=$(curl -sS -o /tmp/korus-auth-policy.json -w "%{http_code}" -X PATCH \
    "$API/api/v1/admin/orgs/$ORG_ID/auth-policy" \
    -H "Authorization: Bearer $KORUS_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$BODY")
  if [ "$code" != "200" ]; then
    echo "[FAIL] auth-policy PATCH HTTP $code"
    cat /tmp/korus-auth-policy.json
    exit 1
  fi
  echo "[OK] auth-policy applied"
fi
