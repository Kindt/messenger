# Spec 013: Live-streaming (full §28)

**Status:** `draft` — L0/L1; implementation blocked until ADR sign-off  
**Parent:** spec 010 US6  
**ADR:** [`docs/adr/ADR-live-streaming-media-stack.md`](../../docs/adr/ADR-live-streaming-media-stack.md)

> **Note:** Renumbered from spec **011** → **012** → **013** (2026-06-15) — spec **011** = Korus Cloud platform; spec **012** = competitor presentation spider-web.

## Goal

Deliver full §28 TZ: WebRTC live ≤200 with E2EE, HLS >200 to 10k viewers, RTMP/SRT ingest, DVR, moderation §28.5.

## Phases

| Phase | Scope |
|-------|--------|
| L0 | ADR Janus vs LiveKit |
| L1 | Contracts REST + NATS + OpenAPI |
| L2 | WebRTC live ≤200 POC |
| L3 | RTMP/SRT ingest |
| L4 | HLS egress + player |
| L5 | DVR + moderation |
| L6 | 10k load test |

## Out of scope (v1)

- Mobile native live broadcast
- External CDN SaaS dependency without on-prem option

## Success criteria

- КУ-26 all-hands 500+ on stage
- p95 HLS latency ≤5 sec
- 2h soak without ingest drop

See [`../010-presentation-gaps-closure/design/multi-stakeholder-spec.md`](../010-presentation-gaps-closure/design/multi-stakeholder-spec.md) § US-LIVE.
