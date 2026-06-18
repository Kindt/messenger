# Enterprise Auth: LDAP, SSO и настройка админом

**Дата:** 2026-06-17  
**Статус:** draft (brainstorming → design)  
**Связь:** `docs/runbooks/sso-keycloak-federation.md`, `docs/PRODUCT_PRESENTATION.md` §12.3, FR-INT-01…03  
**Целевой spec (после утверждения):** `specs/017-enterprise-auth/`

---

## 0. Резюме (маркетолог + аналитик)

**Проблема:** заказчик хочет «как в корпоративном мессенджере» — AD/LDAP, единый вход через портал, без отдельного пароля — но **сам выбирает способ** и **настраивает без разработчиков**.

**Текущее состояние:** инженерия **частичная** — Keycloak + JWT, скрипты LDAP/OIDC, runbook; настройка через **shell + Keycloak Admin Console**, не через админку Korus.

**Целевое состояние (v1 продукта):**

- Админ организации в **встроенной консоли Korus** (`/admin/`) включает нужные способы входа, задаёт приоритет, привязывает секреты (vault).
- Пользователь на экране входа видит **только разрешённые** методы (пароль, LDAP, «Войти через …»).
- Технически все человеческие способы сходятся в **один контракт** — JWT Keycloak → `core-api` / WS (без дублирования auth в Java).

**Позиционирование (маркетинг):**

| Сообщение | Факт |
|-----------|------|
| «Единый вход с корпоративным порталом» | OIDC/SAML broker через Keycloak |
| «Учётки из Active Directory / LDAP» | User Federation, пароль проверяется в AD |
| «Гибкая настройка без форка» | Админ-мастер + политика org |
| «On-prem, в контуре заказчика» | Keycloak + LDAP в том же compose/Ansible |

**Не обещать в v1:** Kerberos/SPNEGO, SCIM, автоматический `admin` из AD-групп, прямой SAML в `core-api`.

---

## 1. Аналитик: требования и границы

### 1.1 Персоны

| Персона | Цель | Боль сейчас |
|---------|------|-------------|
| **Org admin** | Включить AD, отключить саморегистрацию | Нужен SSH и Keycloak Console |
| **IT IdP** | Выдать client_id, LDAP bind | Нет формального handoff-чеклиста в UI |
| **Пользователь** | Войти привычным способом | Только форма логин/пароль в webui |
| **Security** | Секреты не в git, audit | Скрипты с env — ок для ops, не для self-service |
| **Support** | Понять, почему login 401 | Нет статуса federation в админке |

### 1.2 Функциональные требования (FR)

| ID | Требование | Приоритет |
|----|------------|-----------|
| FR-AUTH-01 | Админ включает/выключает способы входа **на уровне org** | P0 |
| FR-AUTH-02 | Поддерживаемые способы v1: **local password**, **LDAP/AD**, **OIDC broker**, **SAML broker** (SAML — P2) | P0/P2 |
| FR-AUTH-03 | Секреты (bind password, client_secret) — **только vault ref**, не plaintext в БД | P0 |
| FR-AUTH-04 | Web UI строит экран входа из **`GET /api/v1/auth/login-options`** | P1 |
| FR-AUTH-05 | После любого способа — тот же JWT pipeline (`AuthService` / `TokenValidator`) | P0 |
| FR-AUTH-06 | Админ видит **статус** провайдера (configured / sync ok / last error) | P1 |
| FR-AUTH-07 | Smoke + runbook для IT заказчика | P0 |
| FR-AUTH-08 | Роль `admin` realm — **вручную** v1; опционально mapper AD group → role v2 | P2 |

### 1.3 Нефункциональные

- Constitution: **не** дублировать IdP в `core-api` (hex exception не требуется — Keycloak остаётся IdP).
- QEMU dev: OpenLDAP fixture для приёмки без реального AD.
- Stage/prod ops: сентябрь 2026+ — live AD приёмка отдельным gate.

### 1.4 Out of scope v1

- Kerberos / Windows SSO без пароля  
- SCIM provisioning  
- Прямой LDAP bind в `core-api`  
- Отдельный IdP на каждую org без Keycloak (свой issuer)

---

## 2. Архитектор: подходы и выбор

### 2.1 Варианты

| # | Подход | Плюсы | Минусы |
|---|--------|-------|--------|
| **A** | Только runbook + Keycloak Console | Уже есть | Нет «настройки админом» в продукте |
| **B** | **Korus Admin → Keycloak Admin API** (рекомендуется) | Один JWT-контур, гибкость, audit в Korus | Нужен service account KC |
| **C** | LDAP/SAML напрямую в `core-api` | Независимость от KC | Два IdP, ломает архитектуру |
| **D** | Realm на каждую org | Изоляция | Операционный ад |

**Рекомендация: B — «Identity Hub»**

```
┌─────────────┐     login-options      ┌──────────────┐
│  Web UI     │ ◄──────────────────────│  core-api    │
│  (динамич.  │     password / OIDC    │  AuthPolicy  │
│   кнопки)   │ ──────────────────────►│  Service     │
└─────────────┘                        └──────┬───────┘
                                              │ Admin API
                                              ▼
                                       ┌──────────────┐
                                       │  Keycloak    │
                                       │  federation  │
                                       │  + brokers   │
                                       └──────┬───────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
              LDAP / AD                  OIDC IdP                   SAML IdP
```

**Принципы:**

1. **Keycloak — единственный issuer** JWT для пользователей.
2. **Korus хранит политику** (что разрешено org, alias, vault refs, UI labels).
3. **Keycloak хранит исполнение** (user federation, identity providers).
4. **Синхронизация:** apply policy → Keycloak (idempotent); drift detection в админке.

### 2.2 Мультитенантность (org)

**v1:** одна политика на org в БД; на экране входа org определяется:

- поддомен / `org_slug` в URL, или  
- выбор org после логина (хуже UX), или  
- единый портал для pilot (одна org).

**v2:** `kc_idp_hint` / org-specific login URL:  
`https://chat.customer.ru/login?org=acme` → только IdP `acme-oidc`.

Keycloak Organizations (KC 24+) — исследовать в Phase 3; не блокер v1.

### 2.3 Модель данных (черновик)

Таблица `org_auth_policy` (Flyway — один владелец на спринт):

| Поле | Описание |
|------|----------|
| `org_id` | FK organizations |
| `allow_local_password` | bool |
| `allow_self_registration` | bool |
| `providers_json` | массив `{ type, alias, enabled, display_name, priority, config_ref, kc_component_id }` |
| `updated_at`, `updated_by` | audit |

`config_ref` → vault path / env key в Ansible, не значение.

Типы `provider.type`: `ldap`, `oidc`, `saml`, `local` (виртуальный).

### 2.4 Публичный API discovery

```
GET /api/v1/auth/login-options?org_slug=acme
→ {
  "org_id": "...",
  "methods": [
    { "id": "local", "type": "password", "label": "Логин и пароль" },
    { "id": "corp-ldap", "type": "ldap", "label": "Корпоративная учётная запись" },
    { "id": "azure", "type": "oidc", "label": "Microsoft Entra ID",
      "authorization_url": "https://kc.../auth?...&kc_idp_hint=azure" }
  ],
  "registration_allowed": false
}
```

Password login остаётся `POST /api/v1/auth/login` (password grant) — для LDAP-fed users Keycloak сам ходит в AD.

---

## 3. Разработчик: фазы реализации

### Phase 0 — «LDAP работает» (2–3 нед.)

| Task | Деталь |
|------|--------|
| T0.1 | Docker OpenLDAP + тестовые пользователи в `docker/` dev overlay |
| T0.2 | Расширить `keycloak-enable-ldap-federation.sh`: `LDAP_VENDOR=ad\|other` |
| T0.3 | QEMU bootstrap: опциональный профиль `auth-ldap` |
| T0.4 | Smoke shell: LDAP user → login API → `/users/me` |
| T0.5 | Playwright `ldap-login.spec.ts`, tier `ui-auth` |

**DoD:** green на QEMU без реального AD.

### Phase 1 — Admin policy + LDAP wizard (3–4 нед.)

| Task | Деталь |
|------|--------|
| T1.1 | Flyway `org_auth_policy` |
| T1.2 | `AuthPolicyService` + `KeycloakAdminClient` (service account) |
| T1.3 | `GET/PATCH /api/v1/admin/orgs/{id}/auth-policy` |
| T1.4 | Admin UI: раздел «Вход / Identity» (`AdminUiContributor`) |
| T1.5 | Wizard LDAP: URL, usersDn, bind (vault ref), test connection |
| T1.6 | Apply → create/update KC user-storage; store `kc_component_id` |
| T1.7 | `GET /api/v1/auth/login-options` (public) |

**DoD:** админ включает LDAP без shell; пользователь LDAP входит через текущую форму `#u`/`#p`.

### Phase 2 — OIDC/SAML + динамический UI (3–4 нед.)

| Task | Деталь |
|------|--------|
| T2.1 | Wizard OIDC (discovery URL, client_id, secret ref) |
| T2.2 | Webui: кнопки из `login-options`; redirect OIDC code flow |
| T2.3 | Callback route / token exchange (или PKCE public client) |
| T2.4 | SAML: шаблон + wizard (metadata URL) или guided Admin export |
| T2.5 | Отключение local password / register из policy |

**DoD:** корпоративный вход кнопкой; LDAP + OIDC на одном экране.

### Phase 3 — Ops hardening (параллельно stage)

| Task | Деталь |
|------|--------|
| T3.1 | Ansible role `korus_auth_policy` |
| T3.2 | LDAPS, cert truststore |
| T3.3 | Scheduled LDAP sync health check |
| T3.4 | Runbook AD приёмка + матрица ролей |
| T3.5 | AD group → realm role mapper (v2, опционально) |

### Затрагиваемые модули

| Модуль | Изменения |
|--------|-----------|
| `core-api` | `AuthPolicy*`, admin resources, KC client adapter |
| `web-client` | login screen, SSO redirect |
| `keycloak/` | examples, realm redirect URIs `:19088` |
| `deploy/ansible` | vault vars, apply playbooks |
| `tests/e2e-web` | `ui-auth`, `ldap-login` |
| `modules/common` | DTO login-options (если shared) |

**Не трогать:** `TokenValidator`, `JwtAuthFilter` (кроме public path для login-options).

---

## 4. Тестировщик: стратегия приёмки

### 4.1 Уровни

| Уровень | Что |
|---------|-----|
| Unit | `AuthPolicyService`, LDAP config validation, vault ref resolution |
| Integration | `KeycloakAdminClient` against Testcontainers KC + OpenLDAP |
| H2 | policy repository CRUD |
| E2E QEMU | login-options → UI → token → thread send |
| Manual ops | Real AD checklist (post Sep 2026) |

### 4.2 Сценарии (Gherkin-кратко)

1. **LDAP password:** given LDAP user, when login API, then 200 + JWT + user in PG.  
2. **LDAP disabled:** when policy off, then login-options не содержит ldap.  
3. **OIDC broker:** when click SSO, then redirect KC → IdP → JWT → `/users/me`.  
4. **Local off:** when only OIDC enabled, then password form hidden.  
5. **Wrong bind:** when admin test connection fails, then status=error, no partial KC state.  
6. **Refresh/logout:** LDAP user refresh + logout 204.  
7. **Rate limit:** login brute-force still 429 (`AuthRateLimiter`).

### 4.3 Tier Playwright

Добавить в `playwright-tiers.json`:

```json
"ui-auth": {
  "specs": ["specs/auth-login-options.spec.ts", "specs/ldap-login.spec.ts"]
}
```

Gate: inner loop при правках auth; outer — с `ui-messaging` smoke.

### 4.4 Критерии sign-off

- [ ] `buildIntegrity` green  
- [ ] OpenLDAP + LDAP login green на QEMU  
- [ ] Admin wizard: enable LDAP without SSH  
- [ ] `login-options` соответствует policy  
- [ ] Runbook IT опубликован  
- [ ] PRODUCT_PRESENTATION §12.3 → «Реализовано (admin + smokes)»

---

## 5. Техпис: артефакты документации

| Документ | Действие |
|----------|----------|
| `specs/017-enterprise-auth/spec.md` | Создать из этого design |
| `specs/017-enterprise-auth/contracts/login-options.openapi.json` | Discovery API |
| `specs/017-enterprise-auth/contracts/auth-policy-admin.openapi.json` | Admin CRUD |
| `docs/runbooks/sso-keycloak-federation.md` | Дополнить: admin UI path, OpenLDAP dev |
| `docs/runbooks/ldap-ad-customer-handoff.md` | **Новый** — для IT заказчика |
| `scripts/SMOKE_INDEX.md` | `smoke-ldap-auth.sh`, tier ui-auth |
| `CHANGELOG.md` | По фазам |
| `AGENTS.md` | Ссылка на spec 017 |
| Admin UI | i18n ru/en pairs для wizard |

**Шаблон handoff для IT (выдержка):**

- LDAP: URL, base DN, bind account, LDAPS cert, sAMAccountName vs uid  
- OIDC: redirect URI, discovery, scopes  
- Контакты для smoke UAT  
- Роли: кто получает `admin` в Keycloak

---

## 6. Пользователь (UX)

### 6.1 Экран входа (целевой)

1. Логотип org (опционально v2).  
2. Блок методов из `login-options` (упорядочены `priority`).  
3. **«Корпоративная учётная запись»** — та же форма логин/пароль (LDAP via KC).  
4. **«Войти через …»** — кнопки OIDC/SAML.  
5. Ссылка «Регистрация» — только если `registration_allowed`.  
6. Ошибки локализованы (`error.auth.*`).

### 6.2 Сценарии

| Пользователь | Действие | Ожидание |
|--------------|----------|----------|
| Сотрудник AD | Ввод доменного логина + пароль | В чат без отдельной регистрации |
| Сотрудник с SSO | Клик «Войти через портал» | Redirect, возврат в чат |
| Внешний подрядчик | Local account (если разрешено) | Register + login |
| Забыл пароль | Keycloak reset flow | Ссылка из админ-политики |

### 6.3 Ограничения UX v1

- Нет Kerberos «без пароля» в браузере.  
- Первый вход LDAP может требовать sync (задержка до N сек) — показать spinner.

---

## 7. Маркетолог: упаковка и roadmap narrative

### 7.1 Уровни предложения

| Tier | Auth |
|------|------|
| **Pilot** | Local + опционально 1 LDAP |
| **Standard** | LDAP + 1 OIDC broker |
| **Enterprise** | Несколько IdP, SAML, политики org, LDAPS |

### 7.2 Конкурентное сравнение

- «AD/LDAP интеграция — стандартный сценарий» (уже в migration cases).  
- Дифференциатор: **admin self-service в консоли Korus**, не только Keycloak.  
- Честно: «приёмка на AD заказчика — совместно с IT» (как E2EE gate).

### 7.3 Формулировки для презентации (после Phase 2)

- Было: «SSO/LDAP — шаблоны и runbook, частично».  
- Стало: «Настраиваемые способы входа в админ-консоли: LDAP/AD, OIDC, SAML; единый JWT-контур».

---

## 8. Сводный backlog (приоритет)

```text
P0  Phase 0 — LDAP e2e на QEMU + smokes
P0  Phase 1 — org auth policy + admin LDAP wizard + login-options (read)
P1  Phase 2 — OIDC UI + dynamic login screen
P2  Phase 2b — SAML wizard
P2  Phase 3 — AD group role mapper, SCIM (отдельный spec)
P3  Kerberos / desktop SSO (out of v1)
```

**Оценка:** ~8–10 недель инженерии до «admin-configurable multi-method» (Phase 0–2); ops AD — после стенда.

---

## 9. Риски

| Риск | Mitigation |
|------|------------|
| Keycloak Admin API drift | Версионировать KC (24), integration tests |
| Секреты в БД | Только vault ref + audit |
| Multi-org IdP на одном realm | `kc_idp_hint` + org_slug в login URL |
| Password grant deprecated | План миграции на Authorization Code + PKCE (Phase 2) |
| MLS + SSO | Независимые контуры; smoke login under MLS active |

---

## 10. Следующий шаг

1. Утвердить design (этот документ).  
2. `/speckit.specify` → `specs/017-enterprise-auth/`.  
3. Реализация **полного v1 сразу** (без pilot-only фазы).

**Решение v1 (максимальный объём):**

- **Org resolution:** поддомен **+** `?org_slug=` **+** fallback на единственную org / `KORUS_DEFAULT_ORG_ID`.
- **Методы:** local password, LDAP/AD, OpenLDAP, OIDC broker, SAML broker — все в admin wizard.
- **UI:** динамический экран входа из `login-options` + OIDC redirect.
- **Инфра:** OpenLDAP в dev compose, Keycloak sync из `AuthPolicyService`.

См. **`specs/017-enterprise-auth/`** — единый backlog.

---

*Роли: аналитик (§1), архитектор (§2), разработчик (§3), тестировщик (§4), техпис (§5), пользователь (§6), маркетолог (§7).*
