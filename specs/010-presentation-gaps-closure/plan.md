# Plan: Spec 010 — Presentation Gaps Closure

**Spec:** [`spec.md`](spec.md)  
**Design:** [`design/multi-stakeholder-spec.md`](design/multi-stakeholder-spec.md)  
**Date:** 2026-06-16  
**Status:** `engineering-closed` (Phase B ops → Sep 2026+)

---

## Phases

### Phase A — Engineering / QEMU (now → Aug 2026)

**Goal:** Prod-ready scaffolds + MVP hardening without stage host.

| US | Tasks | Owner |
|----|-------|-------|
| US1 | CALL-1, CALL-2, CALL-UX, CALL-6 optional | Eng |
| US3 | PUSH-2 compose audit, PUSH-UX onboarding | Eng |
| US4 | BOT-5 Playwright, BOT-6 webhook test | Eng |

**Exit:** all QEMU smokes green; **Playwright outer gate 34/34** (2026-06-16).

### Phase B — Ops / Sign-off (Sep 2026+)

**Goal:** Close US5; unblock US1–US3 prod; US2 sign-off.

| US | Tasks | Owner |
|----|-------|-------|
| US5 | T601–T602, T607 (spec 007) | Ops |
| US1 | CALL-3, CALL-4, CALL-5 optional | Ops + Eng |
| US2 | T603, T606; 8/8 signoff-packet | Ops + Security + QA |
| US3 | PUSH-1 VAPID vault, PUSH-3 E2E manual | Ops |

**Exit:** ops-signoff-log US1/US7; presentation §4 US1–US5 → Реализовано.

### Phase C — Bot API L2 (Q4 2026)

**Goal:** US4 full §17.

| Tasks | Est. |
|-------|------|
| BOT-1 long-poll | 5d |
| BOT-2..4 moderation + rotate | 9d |
| BOT-7 rate limits | 3d |

**Exit:** smoke + Playwright bot tier green.

### Phase D — Live-streaming (12–18 mo)

**Goal:** US6 full §28.

| Gate | Deliverable |
|------|-------------|
| L0 | ADR media stack (OD-4) |
| L1 | spec 013-live-streaming + contracts |
| L2–L6 | WebRTC live → ingest → HLS → DVR → 10k soak |

**Exit:** КУ-26 on stage; §4 Live → Реализовано.

---

## Dependencies

```
US5 (TLS) → US2, US3, US1 prod
US1 (TURN) → US6 L2+
US4 Phase C ⊥ US5 (independent on QEMU)
```

---

## Documentation sync

After Phase B: update `product_status.py`, `PRODUCT_PRESENTATION.md`, rebuild `product_presentation.html`.
