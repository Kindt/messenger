# ADR: Live-streaming media stack (§28 TZ)

**Status:** proposed  
**Date:** 2026-06-16  
**Spec:** [`specs/013-live-streaming/spec.md`](../../specs/013-live-streaming/spec.md)

## Context

Korus Messenger §28 requires full live-streaming: WebRTC+E2EE ≤200 viewers, HLS >200 to 10k, RTMP/SRT ingest, DVR, moderation. Mesh WebRTC calls (§29) do not scale to all-hands.

## Decision (recommended)

Evaluate **Janus Gateway** or **LiveKit self-hosted** as primary SFU/ingress; nginx for HLS egress. Reject mediamtx-only for v1 (insufficient WebRTC live ≤200 + E2EE path).

## Architecture

Separate **media pool** (hot-plug per ADR-hotplug-deployment-split): SFU, ingest, HLS packager, DVR recorder, coturn reuse from web host.

## Consequences

- 12–18 month program; spec 013 before code
- UI entry «Эфир» distinct from «Звонок»
- HLS mode TLS-only without E2EE per TZ

## Alternatives considered

| Option | Rejected because |
|--------|------------------|
| mediamtx only | Weak WebRTC live ≤200 |
| Extend mesh calls | Does not scale to 500+ |
| HLS-only shortcut | PO chose full §28 |
