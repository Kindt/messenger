# Spec 013 — tasks (L2 POC)

## L2 — WebRTC live ≤200 POC

- [x] Flyway `V034__live_sessions.sql`
- [x] REST resources + LiveKit JWT service
- [x] NATS `live.session` + message-pipeline fan-out
- [x] Media capabilities flags
- [x] Web UI «Эфир» panel + LiveKit client
- [x] Docker overlay `docker-compose.livekit-dev.yml`
- [x] Smoke `scripts/smoke-live-session.ps1`
- [x] Unit/H2 tests + `buildIntegrity`

## Backlog (L3–L6)

- [ ] L3 RTMP/SRT ingest
- [ ] L4 HLS egress + player
- [ ] L5 DVR + moderation §28.5
- [ ] L6 10k load soak
