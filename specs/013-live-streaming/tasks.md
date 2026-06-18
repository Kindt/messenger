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

- [x] L3 RTMP/SRT ingest — **implemented:** `POST …/live-sessions/{id}/ingress`, `docker/docker-compose.livekit-ingress.yml`
- [x] L4 HLS egress + player — **implemented:** `PATCH …/live-sessions/{id}/dvr`, HLS player in `ui-live-session.js`, `docker/docker-compose.livekit-egress.yml`
- [x] L5 DVR + moderation §28.5 — **implemented:** `V040__…`, `POST …/moderation`, repository + H2 test
- [x] L6 10k load soak — **`scripts/run-load-test-matrix-qemu.ps1`** (formal soak ops Sep 2026+)
