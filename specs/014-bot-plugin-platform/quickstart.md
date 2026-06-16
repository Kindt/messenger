# Quickstart: Spec 014 — Bot-Plugin Platform

---

## Host (Windows) — build & unit tests

```powershell
cd D:\proj\korus_messenger
.\gradlew :modules:core-api:test --tests "com.avandocmsg.messenger.api.plugins.*" `
  :modules:workers:connector-runtime:test `
  :modules:workers:exchange-bridge:test `
  :modules:workers:storage-bridge:test
python scripts\validate-l0-plugin-menu.py integrations\examples\hr-faq-menu.json
```

---

## Integrations VM (`korus-integrations` · 192.168.76.30)

```powershell
.\scripts\qemu-up.ps1 -WithIntegrations
# host debug: gateway :18190, bridges :18093-:18096
```

```bash
cd /mnt/korus
docker compose -f integrations/docker-compose.integrations.yml up -d --build
curl -fsS http://127.0.0.1:8091/v1/plugin/handle \
  -H 'Content-Type: application/json' \
  -d '{"event_id":"1","type":"mention","text":"ping"}'
```

**Server VM** (`korus-server`): `INTEGRATIONS_BASE_URL=http://192.168.76.30:8091`

---

## Admin API (JWT admin)

| Method | Path |
|--------|------|
| GET | `/api/v1/admin/plugins/presets` |
| GET | `/api/v1/admin/plugins/instances?org_id={uuid}` |
| POST | `/api/v1/admin/plugins/instances/l0` |
| POST | `/api/v1/admin/plugins/instances/{id}/invoke` |

Example L0 body:

```json
{
  "org_id": "00000000-0000-0000-0000-000000000001",
  "bot_name": "hr_faq",
  "display_name": "HR FAQ",
  "config_json": { "welcome_text": "HR", "menu": { "root": ["vacation"], "buttons": [{"id":"vacation","label":"Отпуск","response_text":"Портал"}] } }
}
```

---

## Smokes

```powershell
.\scripts\smoke-plugin-echo-php.ps1 -BaseUrl http://127.0.0.1:18088
.\scripts\smoke-plugin-exchange.ps1 -BaseUrl http://127.0.0.1:18093
.\scripts\smoke-plugin-ocr-mock.ps1 -BaseUrl http://127.0.0.1:18095
.\scripts\smoke-plugin-ai-triage.ps1 -BaseUrl http://127.0.0.1:18096
```

---

## Related

- [`design/qemu-integrations-vm.md`](design/qemu-integrations-vm.md)
- [`contracts/plugin-runtime-api.openapi.yaml`](contracts/plugin-runtime-api.openapi.yaml)
- [`integrations/README.md`](../../integrations/README.md)
