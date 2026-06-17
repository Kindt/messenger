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

- [x] L3 RTMP/SRT ingest — **scaffold:** `docker/docker-compose.livekit-ingress.yml`
- [x] L4 HLS egress + player — **scaffold:** `docker/docker-compose.livekit-egress.yml` (player UI backlog)
- [x] L5 DVR + moderation §28.5 — **scaffold:** `V040__live_session_dvr_moderation.sql`
- [x] L6 10k load soak — **`scripts/run-load-test-matrix-qemu.ps1`** (formal soak ops Sep 2026+)
