# Audio-first mesh calls (§29) — design

**Date:** 2026-06-16  
**Spec:** `specs/010-presentation-gaps-closure` (§A Calls)  
**Status:** approved for L1 implementation (web-client mesh)

## Problem

Mesh «Звонок» always requested camera+audio at start. No dedicated audio conference, no participant avatars when video off, no active-speaker / screen-share highlighting.

## Goals

1. **Audio-first:** default mesh session = `{ audio: true, video: false }`; lower bandwidth and no camera permission until user opts in.
2. **Upgrade on the fly:** enable camera or screen share adds **separate video tracks** + SDP renegotiation (screen already separate; camera becomes addTrack + renegotiate).
3. **Group UX:** highlight **who speaks** (client-side audio level) and **who shares screen**; when video off show **initials avatar** (reuse chat-avatar style).

## Non-goals (this iteration)

- SFU / server-side speaker selection (mesh stays P2P).
- Signaling `speaking` over NATS (local AnalyserNode only).
- Profile photo URLs (initials only; avatar URL later).
- Jitsi mode changes (external UI).

## Architecture

| Layer | Change |
|-------|--------|
| `ui-call-mesh.js` | Avatars, active-speaker loop (Web Audio), slot badges, DOM sync |
| `app.js` | `ensureCallAudio`, `addCallVideoTrack`, `callMediaMode`, peer `display_name` meta |
| CSS | `.rtc-slot-speaking`, `.rtc-slot-sharing`, `.call-participant-avatar` |
| i18n | `ui.call.titleAudio`, `badgeSpeaking`, `badgeSharing`, `enableVideo` |

## Media flow

```
Join mesh → getUserMedia(audio only) → addTrack(audio) per peer
User enables cam → getUserMedia(video) → addTrack(video) → rtcRenegotiateMesh()
User shares screen → getDisplayMedia (unchanged) → separate video track
User disables cam → track.enabled=false → show initials placeholder
```

## Active speaker

- `AudioContext` + `AnalyserNode` per local/remote stream (analyser not connected to speakers — playback via `<video>`).
- Threshold + 400 ms hold; CSS ring on `.rtc-remote-slot` / local stage.
- No server load (client-only).

## Screen share indicator

- `ontrack` with `displaySurface` → `state.rtcSharingPeers[peerId]=true`.
- Local: `state.callScreenStream` → local stage badge «Демонстрация экрана».
- `track.onended` clears flag.

## Acceptance (inner)

- Playwright `conference-rtc.spec.ts` mesh tier still green (mock WebRTC).
- Manual: 2 browsers QEMU — audio join, cam upgrade, screen share badge, initials when cam off.

## Follow-up

- Playwright: mock `getUserMedia` audio-only + assert avatar visible.
- Optional NATS `rtc_signal` kind `speaking` for consistency across clients (if analyser insufficient).
- SFU phase (spec 010 PO) — server load reduction at scale.
