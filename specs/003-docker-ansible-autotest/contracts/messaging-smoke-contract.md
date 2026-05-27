# Messaging Smoke Contract

**Feature**: 003-docker-ansible-autotest  
**Script**: `scripts/smoke-messaging-e2e.sh`

## Preconditions

- Full-server stack running (core-api, ws-gateway, message-pipeline, NATS, Keycloak, PostgreSQL)
- `BASE_URL` default `http://127.0.0.1:8080`
- `WS_URL` default `ws://127.0.0.1:8082/ws`

## Users

| Key | Username | Password |
|-----|----------|----------|
| A | `smoke_user_a` | `smokepass123` |
| B | `smoke_user_b` | `smokepass123` |
| C | `smoke_user_c` | `smokepass123` |

Provisioning: `keycloak-ensure-smoke-users.sh` (register + Keycloak email fix).

## API sequences

### DM (P2P)

```
POST /api/v1/chats  { "type": "p2p", "member_ids": ["<B_uuid>"] }
POST /api/v1/chats/{id}/messages  { "type": "text", "content": "dm-1" }
POST /api/v1/chats/{id}/messages  { "type": "text", "content": "dm-2" }
GET  /api/v1/chats/{id}/messages  (as B) → contains dm-1, dm-2
```

### Group

```
POST /api/v1/chats  { "type": "group", "title": "smoke-group-*", "member_ids": ["<B>", "<C>"] }
POST messages ×3 as A
GET messages as B, C → count ≥ 3
POST reply as B with reply_to_msg_id
GET messages as A → reply present
```

### WebSocket deliver

```
WS connect: {WS_URL}?token={B_token}
POST message as A (unique content ws-marker-*)
Assert: B receives JSON with messageId OR GET as B within 15s
```

### Read receipts

```
POST /api/v1/chats/{id}/messages/{msgId}/read  (as B)
GET  /api/v1/chats/{id}/read-receipts?message_id={msgId}  (as A)
Assert: read_by contains B user id
```

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | All steps passed |
| 1 | Assertion or HTTP failure |
| 2 | Usage / missing dependency |
