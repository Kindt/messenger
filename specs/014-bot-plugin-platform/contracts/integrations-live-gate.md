# Integrations live gate contract (spec 014)

**Status:** ready for QEMU `korus-integrations`  
**Requires live stack:** yes

## Preflight

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

- [ ] `smoke-integrations-gate.ps1` green
- [ ] `plugin-integrations.spec.ts` green with `KORUS_INTEGRATIONS_GATE_URL`
- [ ] Optional: one live backend smoke documented in runbook
