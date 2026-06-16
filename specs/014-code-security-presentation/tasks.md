# Tasks: Spec 014 — Code Security + Presentation

**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)

---

## Phase S1 — Security CI gate

- [x] T1401 S1-1 spotlessCheck + ratchetFrom in buildIntegrity
- [x] T1402 S1-2 benchmark blocking in buildIntegrity / CI
- [x] T1403 S1-3 checkNpmAudit Gradle task + CI
- [x] T1404 S1-4 javac deprecation cleanup (SnapshotPartCodec zstd API)
- [x] T1405 S1-5 JVM test args EnableDynamicAgentLoading
- [x] T1406 S1-6 scripts/security-gate.ps1
- [x] T1407 S1-7 SMOKE_INDEX + CI docs update

## Phase S2 — Security depth

- [x] T1411 S2-1 audit-timing multi-endpoint
- [x] T1412 S2-2 TimingAttackPreventionTest delta guard
- [x] T1413 S2-3 Bot webhook HMAC
- [x] T1414 S2-4 CSP prod ansible default
- [x] T1415 S2-6 docs/SECURITY.md matrix

## Phase PRES — Presentation

- [x] T1421 PRES-1 product_status.py v2.5.4
- [x] T1422 PRES-2 rebuild product + competitor HTML
- [x] T1423 PRES-3 PRODUCT_PRESENTATION.md §24
- [x] T1424 PRES-4 radar methodology footnote
- [x] T1425 PRES-5 brief FAQ ФСТЭК honest

## Phase S3 — §4 tail (QEMU)

- [x] T1431 S3-1 TURN inner gate: `smoke-turn-qemu.ps1 -GuestOnly` documented in SMOKE_INDEX
- [x] T1433 S3-4 k6 QEMU baseline JSON (`deploy/qemu/run/k6-pilot-baseline.json`, fallback probe)
- [x] T1432 S3-3 all-inner Playwright green (34/34, 2026-06-16)

---

**S1** unblocks daily PR gate. **PRES** parallel after T1401 green. **S2/S3** backlog.
