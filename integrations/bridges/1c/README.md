# 1C bridge (spec 014)

**Worker:** `modules/workers/onec-bridge` — Plugin Runtime API on port **8097**.

## Commands

- `ping`
- `/catalog` — OData catalog top N
- `/doc SalesOrder-1001` — document status

## Backends

| Mode | Configuration |
|------|----------------|
| mock/auto | `MOCK_API_BASE` fixtures under `_mock-servers/fixtures/1c/` |
| live | `ONEC_BASE_URL`, `ONEC_USER`, `ONEC_PASSWORD`, optional `ONEC_CATALOG_ENTITY` |

Set `INTEGRATIONS_BACKEND_MODE=live` on integrations guest when credentials are configured.

## Smoke

```powershell
.\scripts\smoke-plugin-1c.ps1 -BaseUrl http://127.0.0.1:18097
```
