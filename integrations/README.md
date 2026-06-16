# Integrations (Spec 014)

Bot/plugin runtime artifacts for **`korus-integrations`** VM (`192.168.76.30`).

## Quick start (integrations guest)

```bash
cd /mnt/korus
docker compose -f integrations/docker-compose.integrations.yml up -d --build
curl -fsS http://127.0.0.1:8091/v1/plugin/handle \
  -H 'Content-Type: application/json' \
  -d '{"event_id":"1","type":"mention","text":"ping"}'
```

## Layout

| Path | Purpose |
|------|---------|
| `docker-compose.integrations.yml` | connector-runtime + mocks + echo + Bitrix + gateway :8090 |
| `docker-compose.vitrine.yml` | overlay for presales light profile |
| `profiles/` | Jira, Confluence, Naumen YAML connector profiles |
| `_mock-servers/` | Static JSON mocks (Jira, Confluence, Naumen, Bitrix) |
| `demos/ocr-invoice-demo/` | On-prem OCR mock (invoice fields) |
| `demos/ai-triage-demo/` | L3 AI triage mock |
| `demos/echo/echo-java8/`, `echo-vbnet48/` | Legacy polyglot echo |
| `schemas/l0-menu.schema.json` | L0 FAQ config validation |
| `sdk/php`, `sdk/python` | Thin HTTP client stubs |

## Server VM

Set on **korus-server** core-api:

`INTEGRATIONS_BASE_URL=http://192.168.76.30:8091`

Admin API: `GET /api/v1/admin/plugins/presets`

## Smokes

```powershell
# PHP sidecar on host port 8088 (when compose published)
.\scripts\smoke-plugin-echo-php.ps1
.\scripts\smoke-plugin-outbound.ps1 -InstanceId <uuid> -Token <token>
.\scripts\smoke-plugin-exchange.ps1
.\scripts\smoke-plugin-ocr-mock.ps1
.\scripts\smoke-plugin-ai-triage.ps1
.\scripts\smoke-plugin-1c.ps1
.\scripts\smoke-integrations-gate.ps1
python scripts\validate-l0-plugin-menu.py integrations\examples\hr-faq-menu.json
```

## Live vs mock backends

`INTEGRATIONS_BACKEND_MODE=auto|mock|live` — см. [`.env.example`](.env.example).  
Java clients: `modules/common/.../plugin/integration/` (Graph, WebDAV, 1C OData, OCR HTTP, LLM).
