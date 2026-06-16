# Spec 014: Code Security Gate + Presentation Sync

**Feature branch:** `014-code-security-presentation`  
**Created:** 2026-06-16  
**Status:** `draft`  
**Input:** [`docs/plans/2026-06-16-code-security-presentation-plan.md`](../../docs/plans/2026-06-16-code-security-presentation-plan.md)

**Out of scope:** ФСТЭК, реестр ПО Минцифры — организационный трек позже; в материалах только честные формулировки.

---

## Goal

| US | Контур | Цель |
|----|--------|------|
| US1 | Security CI gate | `./gradlew buildIntegrity` включает spotless (ratchet), benchmark, npm audit; **zero warnings** в compile/test log |
| US2 | QEMU security smokes | `scripts/security-gate.ps1` — headers, rate-limit, timing audit на живом стеке |
| US3 | Presentation sync | `product_status.py` / §24 / radar — код ↔ HTML без overclaim ФСТЭК |

**Constraint:** stage/prod до **сентября 2026** — E2EE/TLS/Push в презентации остаются «Частично (ops)»; §24 engineering → «Реализовано».

---

## User Story 1 — Security CI gate (P0)

**Independent Test:** `./gradlew buildIntegrity` на Linux CI без `continue-on-error`.

**Acceptance:**

1. **Given** PR, **When** CI runs, **Then** spotlessCheck (ratchetFrom `origin/main`), core-api benchmark, npm audit high — all blocking.
2. **Given** compile, **When** buildIntegrity, **Then** no `warning:` from javac (deprecation tracked in S2).
3. **Given** unit tests, **When** JVM runs, **Then** documented args suppress attach/Unsafe noise.

---

## User Story 2 — QEMU security bundle (P0)

**Independent Test:** `.\scripts\security-gate.ps1 -SkipBuild` при API `:18080`.

**Acceptance:**

1. smoke-security-headers.ps1 green
2. smoke-rate-limit.ps1 green
3. audit-timing.ps1 delta ≤ 5%

---

## User Story 3 — Presentation honesty (P1)

**Acceptance:**

1. §24 «Безопасность» → Реализовано (eng baseline)
2. Radar Korus `reg:3` без inflation
3. `product_presentation.html` v2.5.4+ synced via build scripts

---

## Relationship

| Spec | Link |
|------|------|
| 010 | §4 gaps; Bot L2 done |
| 007 | Ops tail deferred Sep 2026 |
| 012 | Competitor spider / radar methodology |
| 013 | Live-streaming (separate number) |

---

## Non-Goals

- ФСТЭК AT, реестр ПО
- Mobile, live L2–L6
- Stage TLS live deploy
