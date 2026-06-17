# Quickstart: Spec 016 — Chat Message Actions

## API

List messages returns optional `reply_preview` when `reply_to_msg_id` is set:

```bash
curl -fsS "http://127.0.0.1:18080/api/v1/chats/{chatId}/messages?limit=20" \
  -H "Authorization: Bearer $TOKEN"
```

## E2E (QEMU)

```powershell
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
```

## UI deep link

`http://127.0.0.1:19088/?chat={chatId}&msg={messageId}`
