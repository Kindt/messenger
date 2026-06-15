# Spec 010: Presentation Gaps Closure (§4 «Частично» + «Запланировано»)

**Feature branch:** `010-presentation-gaps-closure`  
**Created:** 2026-06-16  
**Status:** `draft`  
**Input:** Закрытие шести доменов [`docs/PRODUCT_PRESENTATION.md`](../../docs/PRODUCT_PRESENTATION.md) §4 — пять «Частично», один «Запланировано».

**Детальная мульти-ролевая проработка (7 ролей × 6 блоков):** [`design/multi-stakeholder-spec.md`](design/multi-stakeholder-spec.md)

---

## Goal

Довести продуктовую презентацию §4 до состояния, когда каждый домен либо **Реализовано**, либо **Запланировано** с явным roadmap — без «Частично» без владельца и footnote.

| US | Домен §4 | Сейчас | Цель |
|----|----------|--------|------|
| US1 | Звонки (WebRTC + TURN) | Частично | Реализовано |
| US2 | E2EE (hybrid MLS) | Частично | Реализовано |
| US3 | Push / PWA | Частично | Реализовано |
| US4 | Bot API | Частично (MVP) | Реализовано (L2) |
| US5 | Prod HTTPS / TLS | Частично | Реализовано |
| US6 | Live-streaming (§28) | Запланировано | Реализовано (full §28) |

---

## Relationship to other specs

| Spec | Relationship |
|------|--------------|
| **007** | US5 ops tail T601–T607; US2 E2EE staging rows |
| **009** | US4 Bot MVP closed; L2 = this spec Phase C |
| **Future 013** | US6 Live-streaming implementation detail (split when L0 ADR starts) |

**Constraint:** stage/prod host **не раньше сентября 2026** — acceptance до этого на **QEMU only**.

---

## User Scenarios & Testing

### User Story 1 — Звонки за NAT (Priority: P1)

Как сотрудник за корпоративным firewall, я хочу стабильный видеозвонок из чата с демонстрацией экрана, чтобы не использовать внешний Zoom.

**Why P1:** КУ-08; единственный eng+ops gap для «Реализовано» звонков — TURN prod.

**Independent Test:** `smoke-turn-qemu.ps1` + manual symmetric NAT matrix; Playwright `conference-rtc.spec.ts`.

**Acceptance Scenarios:**

1. **Given** mesh call в LAN, **When** A звонит B, **Then** audio/video ≤5 сек, screen share visible.
2. **Given** symmetric NAT + TURN prod, **When** A звонит B, **Then** ICE relay candidate used, call established.
3. **Given** ICE failure, **When** timeout 30s, **Then** localized error modal (UX-CALL-3).

**Detailed roles:** design/multi-stakeholder-spec.md § US-CALL

---

### User Story 2 — E2EE prod enable (Priority: P1, blocked by US5)

Как CISO, я хочу formal sign-off hybrid MLS перед включением в prod, чтобы переписка была защищена с согласованной моделью угроз.

**Why P1:** Дифференциатор продукта; eng. ✅, gate = 8/8 human/ops.

**Independent Test:** Playwright e2ee specs on staging HTTPS; `smoke-e2ee-staging.ps1`.

**Acceptance Scenarios:**

1. **Given** `MLS_STATUS=active`, **When** POST plaintext-preview, **Then** 403.
2. **Given** MLS chat, **When** browser send/receive, **Then** roundtrip decrypt OK (Playwright).
3. **Given** signoff-packet, **When** review, **Then** 8/8 rows signed.

**Detailed roles:** design/multi-stakeholder-spec.md § US-E2EE

---

### User Story 3 — Web Push на prod (Priority: P1, blocked by US5)

Как сотрудник, я хочу push-уведомление при закрытой вкладке, чтобы не пропускать сообщения.

**Why P1:** §18 ТЗ web scope; код готов, gap = VAPID vault + HTTPS E2E.

**Independent Test:** `smoke-push-worker-qemu.ps1`; manual notification on staging HTTPS.

**Acceptance Scenarios:**

1. **Given** push-worker healthy, **When** GET :9194/health, **Then** 200.
2. **Given** user subscribed, tab background, **When** new message, **Then** OS notification within 10s.
3. **Given** notification click, **When** user clicks, **Then** app opens target chat.

**Detailed roles:** design/multi-stakeholder-spec.md § US-PUSH

---

### User Story 4 — Bot API полный §17 (Priority: P2)

Как интегратор, я хочу long-poll и moderation APIs, чтобы подключать Service Desk без обязательного HTTPS webhook.

**Why P2:** MVP (spec 009) ✅; L2 closes «Частично» в §4.

**Independent Test:** `smoke-bot-api.ps1`; future Playwright `bot-api.spec.ts`.

**Acceptance Scenarios:**

1. **Given** bot registered (MVP), **When** subscribe + send, **Then** message in history ✅.
2. **Given** long-poll enabled (L2), **When** message in subscribed chat, **Then** event in `GET /v1/bot/updates` within 30s.
3. **Given** @mention in group, **When** webhook configured, **Then** POST to integrator URL with event_id.

**Detailed roles:** design/multi-stakeholder-spec.md § US-BOT

---

### User Story 5 — Prod HTTPS (Priority: P0 ops, Sep 2026+)

Как ops-инженер, я хочу развернуть TLS по runbook, чтобы пользователи работали по HTTPS/WSS и секреты были в vault.

**Why P0 ops:** **Critical path** — блокирует US2, US3, US1 prod validation.

**Independent Test:** `preflight-stage-deploy.ps1` → `stage-tls-smoke.ps1` → ops-signoff US1.

**Acceptance Scenarios:**

1. **Given** stage inventory + vault, **When** `site.yml`, **Then** HTTPS 301 on HTTP.
2. **Given** HTTPS URL, **When** WebSocket connect `/ws`, **Then** WSS without mixed content.
3. **Given** prod deploy, **When** `--tags tls_smoke`, **Then** contract rows green.

**Detailed roles:** design/multi-stakeholder-spec.md § US-TLS

---

### User Story 6 — Live-streaming all-hands (Priority: P3, 12–18 mo)

Как руководитель, я хочу эфир на 500+ сотрудников через HLS, чтобы провести all-hands без Zoom (КУ-26).

**Why P3:** Full §28 ТЗ; 0% code; не блокирует go-live US1–5.

**Scope (PO fixed):** WebRTC+E2EE ≤200, HLS >200 до 10k, RTMP/SRT ingest, DVR, moderation §28.5.

**Independent Test:** future `ui-live` Playwright; 500 viewer load test on stage.

**Acceptance Scenarios:**

1. **Given** host starts «Эфир» (not «Звонок»), **When** 50 viewers WebRTC, **Then** E2EE live mode works.
2. **Given** 500 HLS viewers, **When** stream active, **Then** p95 latency ≤5 sec.
3. **Given** OBS RTMP publish, **When** stream key valid, **Then** viewers see video within 10 sec.

**Detailed roles:** design/multi-stakeholder-spec.md § US-LIVE

---

### Edge Cases (cross-cutting)

- Stage host unavailable until Sep 2026 → QEMU-only acceptance for eng phases
- E2EE push preview must not leak plaintext
- Live «Эфир» vs «Звонок» — separate UI entry points (US6 vs US1)
- iOS Safari PWA push limitations — documented footnote (US3)
- Bot webhook SSRF — validate URL on register (US4)

---

## Requirements

### Functional Requirements (index)

Полный список FR-* по доменам — в [`design/multi-stakeholder-spec.md`](design/multi-stakeholder-spec.md). Ключевые:

- **FR-CALL-001…007:** conference REST, rtc_signal, mesh ≤20, screen share, TURN ICE, ICE failed UX
- **FR-E2EE-001…006:** MLS active gates, migrate-batch, admin status, sign-off
- **FR-PUSH-001…007:** SW, VAPID, push-worker, notification click, vault secrets
- **FR-BOT-001…005:** MVP ✅; L2 long-poll, deleteMessage, pin/ban, token rotate, rate limits
- **FR-TLS-001…005:** HTTPS redirect, WSS, vault, cert renewal, Keycloak HTTPS
- **FR-LIVE-001…006:** live session, 200 threshold, RTMP/SRT, HLS player, moderation, DVR

### Key Entities

- Conference, RtcSignalEvent, LiveSession, StreamKey, Bot (kbt token), Device (push_token)

---

## Success Criteria

### Measurable Outcomes

- **SC-001:** §4 presentation — zero «Частично» without owner+footnote OR all → «Реализовано»
- **SC-002:** `product_status.py` synced; HTML v2.5.x+ rebuilt
- **SC-003:** Playwright outer gate green after Phase A/C
- **SC-004:** ops-signoff-log US1 + US7 complete for US1–US5
- **SC-005:** US6 — spec 013 approved before any live code

### Per-US criteria

See [`contracts/presentation-gaps-acceptance-contract.md`](contracts/presentation-gaps-acceptance-contract.md)

---

## Assumptions

- QEMU dev remains primary eng acceptance until Sep 2026
- coturn stays on web host (ADR accepted)
- Bot L2 requires spec 010 Phase C (not blocking MVP hardening)
- Live full §28 = 12–18 months; Janus vs LiveKit decided in L0 ADR (OD-4)
- Mobile push out of repo scope

---

## Open Decisions

| ID | Question | Owner |
|----|----------|-------|
| OD-1 | Jitsi fallback post-TURN | PO |
| OD-2 | turns:5349 in CALL MVP | PO + Ops |
| OD-3 | E2EE pilot org vs global rollout | PO + CISO |
| OD-4 | Janus vs LiveKit (US6) | Architect |
| OD-5 | Bot rate limit defaults | PO |

---

## Out of Scope

- Mobile iOS/Android clients
- SFU calls >20 (CALL-7)
- E2EE Phase 3 OpenMLS
- SSO/LDAP federation
- Offline PWA messaging

---

## Artifacts

| Artifact | Path |
|----------|------|
| Multi-stakeholder design | [`design/multi-stakeholder-spec.md`](design/multi-stakeholder-spec.md) |
| Implementation plan | [`plan.md`](plan.md) |
| Tasks | [`tasks.md`](tasks.md) |
| Acceptance contract | [`contracts/presentation-gaps-acceptance-contract.md`](contracts/presentation-gaps-acceptance-contract.md) |
| QEMU quickstart | [`quickstart.md`](quickstart.md) |
| Cursor plan | [`.cursor/plans/partial_planned_features_4a58849b.plan.md`](../../.cursor/plans/partial_planned_features_4a58849b.plan.md) |
