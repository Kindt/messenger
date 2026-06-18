# Spec 019 — Engineering gap closure

**Status:** in progress  
**Scope:** закрыть инженерные пробелы из gap-анализа (исключая mobile/desktop клиенты и ops-only stage/prod gates).

## Цель

Довести до production-ready engineering все частично реализованные области: enterprise auth UX, live-streaming L3–L5, AD sync, SCIM, SFU для групповых звонков, PG sharding wire, bot webhook retry, hex tail 2b, worker i18n, Web Push E2E, export manifest, cloud platform ADR.

## Out of scope

- Native mobile (iOS/Android) и desktop-клиенты
- Stage/prod TLS, vault, formal E2EE sign-off, k6 soak на real host (spec 015)
- OpenMLS full external interop (отдельный этап после hybrid MLS)

## User stories

### US1 — Auth admin wizard
Админ org настраивает LDAP/OIDC/SAML через форму `/admin/` без ручного JSON.

### US2 — Live streaming L3–L5
RTMP ingress URL, HLS DVR playback, moderation API + UI.

### US3 — Directory sync
Периодическая синхронизация LDAP → contacts/users; admin status.

### US4 — SCIM 2.0 provisioning
`POST/PATCH/DELETE /scim/v2/Users` для IT-интеграций.

### US5 — Group call SFU
`callMode=livekit` для N>4 участников (reuse LiveKit token service).

### US6 — PG sharding pilot
`OrganizationRoutingDataSource` wired; `OrgRoutingContext` из JWT user org.

### US7 — Bot webhook reliability
Persisted outbox, retry с backoff, metrics.

### US8 — Engineering tail
Hex pin path, worker i18n, Web Push Playwright tier, export package manifest, ADR LB/cell-upgrade.
