# L0+ и действия с сообщениями в чате

План реализации (2026-06-17). Исходный Cursor plan: `l0+_chat_actions`.

## Track A — L0+ (spec 014)

- Шаблоны `{{event.text}}`, `{{user_id}}`, `{{chat_id}}`, `{{config.*}}` (`vars`)
- `slash_commands`, `when` на кнопках
- Schema v2: `integrations/schemas/l0-menu.schema.json`
- `L0MenuConfigValidator` на `POST /api/v1/admin/plugins/instances/l0`

## Track B — Chat message actions (spec 016)

- `reply_preview` в `MessageResponse` (repository join)
- Rich quote UI + `data-testid` на действиях сообщений
- Playwright tier `ui-messaging` (13 specs)

## Verify

```powershell
.\gradlew :modules:core-api:test --tests "*L0*"
python scripts/validate-l0-plugin-menu.py integrations/examples/hr-faq-menu.json
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
```

## Backlog

- Server `GET .../messages/{id}/permalink`
- Admin UI JSON wizard для L0 v2 (сейчас API + quickstart example)
