# Spec 016: Chat Message Actions

**Status:** `in_progress`  
**Created:** 2026-06-17

## Goal

Parity API ↔ UI ↔ E2E for message actions in chat: reply with rich quote, permalink, forward, delete, edit.

## User stories

| US | Summary | Status |
|----|---------|--------|
| US1 | Reply with `reply_to_msg_id` + rich quote in UI | in progress |
| US2 | Click quote → scroll/highlight parent | in progress |
| US3 | Copy/open message deep link `?chat=&msg=` | in progress |
| US4 | Forward to another chat | in progress |
| US5 | Delete own message (soft) | in progress |
| US6 | Edit own text message | in progress |
| US7 | E2EE threads: reply preview without leaking ciphertext | in progress |

## Contracts

- [`contracts/message-reply-preview.json`](contracts/message-reply-preview.json)

## Related

- L0+ menu v2: spec **014** T01432
- E2E tier: `ui-messaging` in `tests/e2e-web/playwright-tiers.json`
