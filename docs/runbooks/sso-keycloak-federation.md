# SSO / OIDC / LDAP через Keycloak (P2-2)

**Аудитория:** ops, администратор IdP, интегратор.  
**Scope:** единый вход в мессенджер без отдельного пароля в UI; мессенджер **не** реализует SSO — только потребляет JWT от Keycloak.

## Предусловия

- Keycloak realm **`avandocmsg`** (импорт `keycloak/avandocmsg-realm.json` при старте контейнера).
- Клиент **`messenger-web`**: redirect URI вашего UI (`https://<host>/*`, для dev `http://127.0.0.1:19088/*`).
- Секреты IdP — только в vault / env, **не** в git.

## Шаблоны в репозитории

| Файл | Назначение |
|------|------------|
| `keycloak/avandocmsg-realm.json` | Realm + **отключённые** IdP Google/Yandex + роли user/admin |
| `keycloak/identity-provider-corporate-oidc.example.json` | Generic OIDC (Azure AD, Okta, corporate portal) |
| `keycloak/user-federation-ldap.example.json` | LDAP/AD user federation |
| `scripts/keycloak-enable-identity-provider.sh` | Включение IdP через Admin REST (curl) |

## OIDC (Google / корпоративный portal)

1. В IdP заказчика зарегистрируйте OAuth client: redirect  
   `https://<KEYCLOAK_HOST>/realms/avandocmsg/broker/<alias>/endpoint`
2. Скопируйте `client_id` / `client_secret` в vault.
3. На хосте с Keycloak (QEMU server guest или stage):

```bash
export KEYCLOAK_URL=http://127.0.0.1:8080
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD='***'
export SSO_IDP_ALIAS=corporate-oidc
export SSO_CLIENT_ID='***'
export SSO_CLIENT_SECRET='***'
export SSO_DISCOVERY_URL='https://login.example.com/.well-known/openid-configuration'
bash scripts/keycloak-enable-identity-provider.sh
```

4. Keycloak Admin → **Identity providers** → `<alias>` → **First login flow**: создайте пользователя или link existing.
5. **Authentication** → **Browser flow**: добавьте кнопку IdP на login theme (или используйте стандартную страницу Keycloak с IdP links).
6. Web UI: вход через Keycloak Authorization Code — redirect на `/realms/avandocmsg/protocol/openid-connect/auth?client_id=messenger-web&...`

### FR-INT-02: отключение локального пароля

Keycloak → **Authentication** → **Required actions** / **Realm settings** → **Login** → отключить регистрацию; для org — политика через **Identity provider** «Hide on login page» для local users (Advanced).

## LDAP / Active Directory

1. Keycloak Admin → **User federation** → **Add LDAP**.
2. Параметры см. `keycloak/user-federation-ldap.example.json` (URL, `usersDn`, `bindDn`, `bindCredential` из vault).
3. **Sync mode:** `IMPORT` или `LEGACY` по политике заказчика.
4. Mapper **username** / **email** → realm attributes; роль `admin` назначается **вручную** (FR-INT-03).

## Проверка

```bash
# Master token + realm
curl -fsS "$KEYCLOAK_URL/realms/avandocmsg/.well-known/openid-configuration" | head
# После login через IdP — JWT с iss = ваш issuer; API:
curl -fsS -H "Authorization: Bearer $TOKEN" http://127.0.0.1:18080/api/v1/users/me
```

## Ограничения

- **Stage/prod FQDN** — не раньше **сентября 2026** (см. `AGENTS.md`); на Windows dev — QEMU `:18080` / `:19088`.
- SAML IdP — через Keycloak SAML broker (отдельная настройка Admin Console; паттерн как OIDC).
- Маппинг ролей org-admin — документируется в приложении к договору, не автоматически из AD groups в v1.

## Связанные документы

- `docs/PRODUCT_PRESENTATION.md` §12.3  
- `deploy/ansible/inventory/stage/group_vars/vault.yml.example` — `korus_keycloak_admin_password`  
- `docker/docker-compose.keycloak-prod.yml` — prod Keycloak overlay
