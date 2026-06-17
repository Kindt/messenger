# Keycloak realm import (dev / CI)

Only **`avandocmsg-realm.json`** is mounted into Keycloak `--import-realm`.

Example IdP/LDAP JSON files live under **`examples/*.json.example`** — they are **not** imported at startup (Keycloak 24 rejects invalid example realms and exits with code 1).

After stack up, CI runs **`scripts/keycloak-ensure-dev-users.sh`** and **`scripts/keycloak-verify-realm.sh`**.
