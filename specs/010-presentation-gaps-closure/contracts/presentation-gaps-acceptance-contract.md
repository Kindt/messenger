# Contract: Presentation Gaps Acceptance (Spec 010)

**Version:** 1.0  
**Date:** 2026-06-16  
**Spec:** [`../spec.md`](../spec.md)

---

## Purpose

Formal acceptance criteria for transitioning §4 presentation statuses from **Частично** / **Запланировано** to **Реализовано**.

---

## US1 — Звонки

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | Mesh call LAN 2 users audio+video | Manual M1 or Playwright mock |
| 2 | Screen share visible remote | Manual M4 |
| 3 | TURN prod TCP :3478 reachable | `smoke-turn.ps1 -TurnHost <fqdn>` |
| 4 | Symmetric NAT call via relay | Manual M2 or CALL-6 smoke |
| 5 | ICE failed shows i18n UX | Manual / screenshot |
| 6 | §4 status → Реализовано | product_status.py `calls=done` |

---

## US2 — E2EE

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | signoff-packet 8/8 signed | `e2ee-security-signoff-packet-2026-06-15.md` |
| 2 | plaintext-preview 403 MLS active | unit + smoke |
| 3 | Playwright roundtrip staging HTTPS | T606 |
| 4 | NATS mls consumer 24h staging | smoke-e2ee-staging row 4 |
| 5 | §4 status → Реализовано | product_status.py `e2ee=done` |

---

## US3 — Push / PWA

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | push-worker health prod | `:9194/health` |
| 2 | Real notification HTTPS staging | Manual sign-off |
| 3 | Notification click opens chat | Manual |
| 4 | VAPID not in git | vault audit |
| 5 | §4 status → Реализовано | product_status.py `push=done` |

---

## US4 — Bot API

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | MVP smoke green | `smoke-bot-api.ps1` |
| 2 | Long-poll returns events ≤30s | integration test (L2) |
| 3 | Webhook delivery with event_id | BOT-6 test |
| 4 | Playwright bot tier green | bot-api.spec.ts (L2) |
| 5 | §4 status → Реализовано | product_status.py `bot_api=done` |

---

## US5 — Prod HTTPS

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | Preflight green | `preflight-stage-deploy.ps1` |
| 2 | TLS smoke staging | `stage-tls-smoke.ps1` |
| 3 | Prod tls_smoke tag | ansible T607 |
| 4 | ops-signoff US1 rows 1–5 | ops-signoff-log.md |
| 5 | §4 status → Реализовано | product_status.py `tls=done` |

---

## US6 — Live-streaming

| Row | Criterion | Verification |
|-----|-----------|--------------|
| 1 | spec 013 approved | speckit sign-off |
| 2 | 500 HLS viewers p95 latency ≤5s | load test report |
| 3 | RTMP ingest OBS → player | smoke + manual |
| 4 | Moderation stop stream | API test |
| 5 | 2h soak no ingest drop | stage report |
| 6 | §4 status → Реализовано | product_status.py `live=done` |

---

## Program gate

All US1–US5 rows above satisfied **OR** remaining «Частично» have documented owner, footnote, target date in presentation §4.

US6 may remain **Запланировано** with spec 013 link until Phase D complete.
