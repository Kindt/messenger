# Tasks: Spec 010 — Presentation Gaps Closure

**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)

---

## Phase A — QEMU Engineering

- [x] T101 CALL-1 coturn `--external-ip` / `--relay-ip` in prod compose
- [x] T102 CALL-2 prod inventory `korus_turn_host`, `korus_web_turn_prod`
- [x] T103 CALL-UX i18n ICE failed modal in webui
- [x] T104 CALL-6 optional relay smoke script
- [x] T105 PUSH-2 push-worker prod/pilot compose profile audit
- [x] T106 PUSH-UX notifications onboarding tooltip + i18n
- [x] T107 BOT-5 Playwright `bot-api.spec.ts` + tier manifest
- [x] T108 BOT-6 webhook delivery test with mock HTTP server
- [x] T109 Verify smokes: `smoke-turn-qemu.ps1`, `smoke-push-worker-qemu.ps1`, `smoke-bot-api.ps1` — guest OK (2026-06-16)
- [x] T110 Playwright outer gate — **33/33** `npx playwright test` on QEMU `:19088`/`:18080` (2026-06-16)

## Phase B — Ops (Sep 2026+, spec 007 overlap)

- [ ] T201 T601 stage DNS + vault + site.yml *(blocked: no host until Sep 2026)*
- [ ] T202 T602 stage-tls-smoke.ps1 green
- [ ] T203 T607 prod tls_smoke tag
- [x] T204 CALL-3 firewall relay ports runbook / UFW *(eng: runbook §F)*
- [ ] T205 CALL-4 vault `korus_coturn_secret` *(ops)*
- [ ] T206 CALL-5 optional turns:5349 overlay
- [ ] T207 PUSH-1 VAPID keys in vault + deploy *(ops)*
- [ ] T208 PUSH-3 manual E2E notification on HTTPS *(ops)*
- [ ] T209 T603 E2EE staging smokes rows 4–6 *(ops)*
- [ ] T210 T606 Playwright staging HTTPS formal *(ops)*
- [ ] T211 E2EE signoff-packet 8/8 signatures *(ops)*
- [ ] T212 Update product_status.py + presentation HTML *(after ops sign-off)*

## Phase C — Bot API L2

- [x] T301 BOT-1 `GET /v1/bot/updates` long-poll + event queue
- [x] T302 BOT-2 bot deleteMessage endpoint
- [x] T303 BOT-3 bot pin/ban/mute wrappers
- [x] T304 BOT-4 token rotation API
- [x] T305 BOT-7 rate limit filter per bot_id
- [x] T306 smoke-bot-api extended + Playwright tier green

## Phase D — Live-streaming (12–18 mo)

- [x] T401 L0 ADR Janus vs LiveKit (OD-4)
- [x] T402 L1 create spec 013-live-streaming + contracts (was 012; renumbered 2026-06-15)
- [ ] T403 L2 WebRTC live ≤200 POC
- [ ] T404 L3 RTMP/SRT ingest + stream key API
- [ ] T405 L4 HLS egress + webui player
- [ ] T406 L5 DVR + moderation §28.5
- [ ] T407 L6 10k viewer load test + ui-live Playwright

---

**Phase A** engineering complete; T110 outer gate optional before merge. **Phase B** blocked until Sep 2026 host. **Phase D** L0–L1 done; L2–L6 backlog per PO full §28 scope.

**Engineering closure:** 2026-06-16 — Phases A/C + D L0–L1; Phase B ops deferred Sep 2026+.
