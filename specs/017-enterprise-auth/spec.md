# Spec 017 — Enterprise Auth (LDAP / SSO, admin-configurable)

## Goal

Максимальная свобода выбора способа входа **на уровне организации**, настройка **админом** в консоли Korus. Единый JWT-контур через Keycloak.

## User stories

| ID | Story |
|----|-------|
| US1 | Org admin включает LDAP/AD без shell |
| US2 | Org admin включает OIDC и SAML brokers |
| US3 | Пользователь видит только разрешённые методы на экране входа |
| US4 | Org определяется поддоменом, `org_slug` или default org |
| US5 | IT заказчик получает runbook handoff |
| US6 | QEMU dev: OpenLDAP + green smokes |

## Methods v1

- `password` — local + LDAP-fed (Keycloak password grant)
- `ldap` — user federation (AD / OpenLDAP)
- `oidc` — identity broker
- `saml` — identity broker

## Out of scope

Kerberos, SCIM, direct LDAP in core-api, auto AD group → admin (v2).

## Contracts

- `contracts/login-options.json`
- `contracts/auth-policy-admin.json`
