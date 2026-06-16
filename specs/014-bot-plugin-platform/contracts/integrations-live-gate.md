# Integrations live gate contract (spec 014)

**Status:** ready for QEMU `korus-integrations`  
**Requires live stack:** yes

## Live stand checklist

When real credentials are available on integrations node:

1. Copy `integrations/.env.example` → `integrations/.env`
2. Set `INTEGRATIONS_BACKEND_MODE=live` and backend URLs/tokens
3. Set org policy: `POST /api/v1/admin/plugins/policies?org_id=...`
4. `.\scripts\qemu-sync-integrations.ps1` (full rebuild)
5. `.\scripts\smoke-integrations-gate.ps1` then optional per-backend spot checks
6. Mark T01431 in `tasks.md`

## Preflight

```powershell
.\scripts\integrations-gate-preflight.ps1 -Online
```

1. `.\scripts\qemu-up.ps1 -WithIntegrations` (or warm stack)
2. API: `http://127.0.0.1:18080/api/v1/health`
3. Gateway: `http://127.0.0.1:18190/health`

## Host smokes (mock/auto backends)

```powershell
.\scripts\smoke-integrations-gate.ps1
```

## Playwright (API admin + optional gateway)

```powershell
$env:KORUS_INTEGRATIONS_GATE_URL = "http://127.0.0.1:18190"
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
$env:KORUS_API_URL = "http://127.0.0.1:18080"
cd tests/e2e-web
npx playwright test specs/plugin-integrations.spec.ts
```

## Live backends (optional, on integrations guest)

Copy `integrations/.env.example` → `integrations/.env`, set credentials, `INTEGRATIONS_BACKEND_MODE=live`, redeploy compose on guest.

| Backend | Env vars |
|---------|----------|
| Graph | `GRAPH_ACCESS_TOKEN` or tenant/client/secret |
| WebDAV | `WEBDAV_BASE_URL`, `WEBDAV_USER`, `WEBDAV_PASSWORD` |
| 1C OData | `ONEC_BASE_URL`, `ONEC_USER`, `ONEC_PASSWORD` |
| OCR | `OCR_HTTP_URL` (on-prem) |
| LLM | `LLM_ON_PREM_URL` and/or `LLM_BASE_URL`, `LLM_API_KEY` |

Org policy (`llm_mode`, `ocr_on_prem_only`) via `POST /api/v1/admin/plugins/policies?org_id=...`.

## Sign-off criteria

### Local / QEMU mock gate (2026-06-16)

- [x] Unit tests: `common.plugin.integration.*`, `api.plugins.*`, bridge workers
- [x] `integrations-gate-preflight.ps1 -Online`
- [x] `smoke-integrations-gate.ps1` green (mock/auto)
- [x] `plugin-integrations.spec.ts` green with `KORUS_INTEGRATIONS_GATE_URL`

### Live stand (requires real credentials)

- [ ] `integrations/.env` on guest with `INTEGRATIONS_BACKEND_MODE=live`
- [ ] Optional: one live backend smoke documented in runbook (Graph / WebDAV / 1C / OCR / LLM)
