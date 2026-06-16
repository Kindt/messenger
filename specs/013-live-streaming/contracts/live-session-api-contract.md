# Live-streaming REST + NATS contract (spec 013 L2 POC)

**Status:** L2 implemented (WebRTC ≤200 via LiveKit SFU)  
**ADR:** [`docs/adr/ADR-live-streaming-media-stack.md`](../../docs/adr/ADR-live-streaming-media-stack.md)

## REST

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/chats/{chatId}/live-sessions` | Start live session in chat (host) |
| GET | `/api/v1/chats/{chatId}/live-sessions?active_only=true` | List sessions |
| GET | `/api/v1/live-sessions/{sessionId}` | Session card |
| POST | `/api/v1/live-sessions/{sessionId}/join` | Join; returns LiveKit JWT |
| POST | `/api/v1/live-sessions/{sessionId}/leave` | Leave |
| POST | `/api/v1/live-sessions/{sessionId}/end` | End (host or chat admin) |

### Join response (`JoinLiveSessionResponse`)

- `live_session_id`, `room_name`, `livekit_url`, `access_token`, `role` (`host` \| `viewer`), `viewer_count`, `max_viewers`

### Media capabilities

`GET /api/v1/media/capabilities` adds:

- `live_streaming_enabled` — true when `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` set
- `livekit_url` — WebSocket URL for clients
- `live_max_webrtc_viewers` — cap (default 200)

## NATS

| Subject | Payload | Publisher | Consumer |
|---------|---------|-----------|----------|
| `live.session` | `LiveSessionChangeEvent` | core-api `LiveSessionService` | message-pipeline → `msg.deliver.{userId}` |

Event fields: `change` (`created` \| `updated` \| `ended`), `live_session_id`, `chat_id`, `actor_id`, `title`, `status`, `mode`, `room_name`, `provider`, `viewer_count`, `max_viewers`.

## Env (core-api)

| Env | Property | Notes |
|-----|----------|-------|
| `LIVEKIT_URL` | `livekit.url` | e.g. `ws://livekit:7880` or `wss://…` |
| `LIVEKIT_API_KEY` | `livekit.api.key` | LiveKit API key |
| `LIVEKIT_API_SECRET` | `livekit.api.secret` | HS256 secret |
| `LIVESTREAM_MAX_WEBRTC_VIEWERS` | `livestream.max.webrtc.viewers` | default 200 |
| `LIVESTREAM_ROOM_PREFIX` | `livestream.room.prefix` | default `korus-live-` |

## UI

Call panel section **«Прямой эфир»** (`ui-live-session.js`) — separate from mesh/Jitsi **«Звонок»**.

## Out of scope (L2)

- RTMP/SRT ingest (L3), HLS egress (L4), DVR (L5), 10k load (L6)
- E2EE on live WebRTC path (future)
