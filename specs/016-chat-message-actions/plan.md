# Plan: Spec 016 — Chat Message Actions

**Date:** 2026-06-17

## Phase 1 — API

- `MessageReplyPreview` on `MessageResponse`
- `MessageRepository` LEFT JOIN parent on list/get

## Phase 2 — UI

- Rich `.msg-reply-quote` using `reply_preview`
- `data-testid` on message action buttons

## Phase 3 — E2E

- Extend `messaging-actions.spec.ts`
- Tier `ui-messaging`
