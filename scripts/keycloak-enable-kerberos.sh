#!/usr/bin/env bash
# Keycloak Kerberos/SPNEGO broker scaffold (spec 019 US9).
# Configure realm Kerberos auth in Keycloak Admin; desktop SSO requires client support.
set -euo pipefail

KC_URL="${KEYCLOAK_URL:-http://127.0.0.1:8080}"
REALM="${KEYCLOAK_REALM:-messenger}"
KERBEROS_REALM="${KERBEROS_REALM:-EXAMPLE.COM}"
KDC="${KERBEROS_KDC:-kerberos.example.com}"
KEYTAB_PATH="${KERBEROS_KEYTAB_PATH:-/etc/keycloak/korus.keytab}"

echo "Kerberos broker setup (manual Admin Console steps):"
echo "  1. Realm $REALM -> Authentication -> Add Kerberos execution"
echo "  2. User federation -> kerberos realm=$KERBEROS_REALM kdc=$KDC keytab=$KEYTAB_PATH"
echo "  3. Browser flow: SPNEGO before forms (Web SSO)"
echo "  4. Test: kinit user@$KERBEROS_REALM && curl --negotiate -u : $KC_URL/realms/$REALM/account"
echo "See docs/runbooks/kerberos-keycloak-handoff.md"
