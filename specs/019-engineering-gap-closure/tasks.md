# Spec 019 — Engineering gap closure — Tasks

**Status:** engineering closed + outer gate 50/50 QEMU (2026-06-18)

## US1 — Auth admin wizard

- [x] T01901 Admin UI `core-auth-policy` — форма GET/PATCH policy, providers JSON
- [x] T01902 `POST .../auth-policy/test` — LDAP TCP + JNDI bind
- [x] T01903 `AuthPolicyServiceTest` testPolicy cases

## US2 — Live streaming L3–L5

- [x] T01911 DVR fields in API/repository (`dvr_playlist_url`, `moderation_state`)
- [x] T01912 REST moderation, DVR patch, RTMP ingress
- [x] T01913 HLS player in `ui-live-session.js`
- [x] T01914 `LiveSessionRepositoryH2Test` moderation/DVR

## US3 — Directory sync

- [x] T301–T305 (see prior batch)

## US4 — SCIM 2.0

- [x] T401–T404 (see prior batch)

## US5 — Group call SFU (LiveKit)

- [x] T01951 `POST /chats/{id}/calls/livekit/join`
- [x] T01952 `group_call_sfu_enabled` in media capabilities
- [x] T01953 `ui-call-livekit.js` + call panel mode
- [x] T01954 `ChatCallLiveKitServiceTest`

## US6 — PG sharding pilot

- [x] T01961 `OrganizationRoutingDataSource` wired in `MessengerApplication`
- [x] T01962 `OrgRoutingFilter` + clear filter
- [x] T01963 `ADR-pg-sharding-pilot.md`

## US7 — Bot webhook reliability

- [x] T01971 `V044__bot_webhook_outbox.sql`
- [x] T01972 Retry scheduler in `BotDeliveryWorker`
- [x] T01973 `BotWebhookOutboxTest`

## US8 — Engineering tail

- [x] T01981 Hex 2b pin → `MessageApplicationService`
- [x] T01982 Worker i18n log strings
- [x] T01983 Web Push tier `ui-push` + `web-push.spec.ts`
- [x] T01984 Export `package-manifest.json`
- [x] T01985 `ADR-platform-lb-cells.md` (T01127)

## US9 — Kerberos scaffold

- [x] T01991 `scripts/keycloak-enable-kerberos.sh`
- [x] T01992 `docs/runbooks/kerberos-keycloak-handoff.md`

## US10 — SCIM Groups

- [x] T019101 `V045__scim_groups.sql` + `ScimGroupRepository`
- [x] T019102 `ScimGroupsResource` GET/POST/PATCH/DELETE `/scim/v2/Groups`
- [x] T019103 `ScimGroupsResourceTest` H2 round-trip
- [x] T019104 `KeycloakAuthSyncClient.ldapAdminGroupMapper` on `admin_group_dn`
- [x] T019105 `AuthPolicyService` passes `org_id` + provider `admin_group_dn` on LDAP apply
- [x] T019106 `ScimGroupsResource` registered in `JerseyConfig`

## US10 — SCIM Groups + AD admin group mapper

- [x] T019101 Flyway `V045__scim_groups.sql`
- [x] T019102 `ScimGroupsResource` CRUD `/scim/v2/Groups`
- [x] T019103 `ScimGroupsResourceTest`
- [x] T019104 LDAP `admin_group_dn` → Keycloak role-ldap-mapper
- [x] T019105 Playwright `ui-live` tier (livekit-sfu, admin-auth-policy, live-session-moderation)

## US11 — Hex 2b read-path + Chat 2a

- [x] T019111 Message read-path via `MessageApplicationService`
- [x] T019112 `BotService` delete/pin via application layer
- [x] T019113 `ChatResource.getById` via `ChatApplicationService`

## US12 — Hex 2c + MessageService HTTP retirement (phase 3)

- [x] T019121 `IndexerEventPublisher` + coordinator wiring (hot-plug indexer queue on production path)
- [x] T019122 `MessageResource` / `JerseyConfig` / `MessengerApplication` without `MessageService`
- [x] T019123 `ScimGroupRepositoryPort` + `JdbcScimGroupRepositoryAdapter`

## Deferred (explicit)

- [ ] OpenMLS full external interop — Phase 3 (`docs/E2EE_ARCHITECTURE.md`)
- [ ] Hex 2c DirectorySync / SCIM Users on ports — low risk tail
- [ ] T01124 cell-upgrade 2-Cell idempotency — **partial:** `scripts/smoke-cell-upgrade-idempotency.ps1`; LSO-020 Sep 2026+
- [ ] GDPR strict prod — legal + `EXPORT_COMPLETENESS_STRICT` on stage (ops)
