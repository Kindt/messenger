# E2EE security gate sign-off (engineering)

**Date**: 2026-06-10  
**Scope**: web-client client-side MLS (Web Crypto) + `GET /e2ee/mls/session/{chatId}`

| # | Check | Status |
|---|-------|--------|
| 1 | ADR hybrid documented (`docs/adr/ADR-e2ee-mls-library.md`) | done |
| 2 | `/plaintext-preview` 403 when `mls_status=active` | covered by API test |
| 3 | Client avoids plaintext-preview when MLS active (`app.js` + `ui-e2ee-mls.js`) | done |
| 4 | NATS `mls.*` consumer in staging | ops (QEMU/server playbook) |
| 5 | Batch migration in staging | ops |
| 6 | `GET /admin/e2ee/status` | ops |
| 7 | Legacy `e2ee_scheme=legacy` smokes | unchanged |
| 8 | Playwright `e2ee-capabilities.spec.ts` + `e2ee-browser-roundtrip.spec.ts` | added |

**Product / security formal sign-off** still required before prod `MLS_STATUS=active`.
