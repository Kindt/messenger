# E2EE security sign-off packet (rows 1–3)

**Date:** 2026-06-15  
**Audience:** Product, Engineering, Security  
**Staging ops:** [`e2ee-staging-checklist.md`](e2ee-staging-checklist.md) (rows 4–6)

---

## Row 1 — ADR hybrid (Product + Engineering)

| Evidence | Location |
|----------|----------|
| ADR accepted for implementation | [`docs/adr/ADR-e2ee-mls-library.md`](../adr/ADR-e2ee-mls-library.md) |
| Architecture overview | [`docs/E2EE_ARCHITECTURE.md`](../E2EE_ARCHITECTURE.md) |
| WASM spike notes | [`docs/review/e2ee-wasm-spike-2026-06-10.md`](e2ee-wasm-spike-2026-06-10.md) |

**Sign when:** hybrid model (server wire + client Web Crypto) accepted for pilot/stage; OpenMLS full client deferred per ADR.

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Product | | | |
| Engineering | | | |

---

## Row 2 — `/plaintext-preview` → 403 when MLS active (Security)

| Evidence | Location |
|----------|----------|
| API behaviour | `MessageResource` plaintext-preview path |
| Automated | `.\gradlew.bat :modules:core-api:test --tests "*Mls*"` — **PASS** |
| Contract | [`docs/contracts/e2ee-mls-contract.md`](../../docs/contracts/e2ee-mls-contract.md) |

**Sign when:** Security confirms server never returns plaintext preview for MLS-active chats.

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Security | | | |

---

## Row 3 — Client skips plaintext-preview when MLS active (Security)

| Evidence | Location |
|----------|----------|
| Client gate | `modules/web-client/.../webui/ui-e2ee-mls.js`, `app.js` |
| Playwright | `tests/e2e-web/e2ee-capabilities.spec.ts`, `e2ee-browser-roundtrip.spec.ts` — **PASS on QEMU** |
| Engineering checklist | [`e2ee-security-gate-signoff-2026-06-10.md`](e2ee-security-gate-signoff-2026-06-10.md) row 3 |

**Sign when:** code review confirms no client-side plaintext-preview fetch when `mls_status=active`.

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Security | | | |

---

## After rows 1–3

1. Execute staging checklist (rows 4–6).  
2. QA formal sign row 8 (Playwright on staging URL).  
3. Update [`ops-signoff-log.md`](../../docs/review/ops-signoff-log.md) US7.  
4. Only then consider prod `MLS_STATUS=active`.
