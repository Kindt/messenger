# Playwright E2E (spec 003 / US9 tiers)

Browser tests for web-client critical paths and parity-matrix domains.

## Prerequisites

- QEMU stack up: API `http://127.0.0.1:18080`, UI `http://127.0.0.1:19088`
- Smoke users seeded in server guest (`keycloak-ensure-smoke-users.sh`)

## Multi-user (group) browser tests

Parallel clients use **`fixtures/group-users.ts`**: one isolated `BrowserContext` per smoke user (`smoke_user_a/b/c`).

```typescript
const sessions = await openGroupUserSessions(browser, ["smoke_user_a", "smoke_user_b"]);
try {
  await openGroupChatForAll(sessions, title);
  await uiSendAndExpectDelivery(sessionPage(sessions, "smoke_user_a"), [sessionPage(sessions, "smoke_user_b")], text, title);
} finally {
  await closeGroupUserSessions(sessions);
}
```

Specs: `messaging-group-users.spec.ts` (A→B delivery, 3-user fan-out, B→A reply). Delivery uses WS when online; otherwise REST thread reload (QEMU host).

Optional: `PLAYWRIGHT_WS_URL=ws://127.0.0.1:19088/ws` overrides patched `web-client-env.js` WS URL.

## Inner loop (US9 — fast acceptance)

Stack once, then iterate by tier (host browser against forwarded ports):

```powershell
.\scripts\qemu-up.ps1 -KeepDisks
.\scripts\qemu-stack-wait.ps1

.\scripts\playwright-dev-loop.ps1 -Tier api
.\scripts\playwright-dev-loop.ps1 -Tier ui-visual
.\scripts\playwright-dev-loop.ps1 -Tier ui-conversation
.\scripts\playwright-dev-loop.ps1 -Tier ui-call-flows
.\scripts\playwright-dev-loop.ps1 -Tier ui-admin-extended
.\scripts\playwright-dev-loop.ps1 -Tier ui-interaction-audit
.\scripts\playwright-dev-loop.ps1 -Tier ui-i18n-artifacts
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
```

Tier manifest: `playwright-tiers.json`. Status: `deploy/qemu/run/inner-tier-status.json`.

| Tier | Specs | Typical time |
|------|-------|--------------|
| `api` | profile-settings, e2ee-capabilities, contacts API | &lt; 90s |
| `ui-auth` | auth-session | &lt; 60s |
| `ui-mobile` | mobile-shell (375/390/768/1280/1920 viewports) | &lt; 2m |
| `ui-visual` | visual guards (3-user desktop + phone thread screenshots) | &lt; 2m |
| `ui-conversation` | multi-user A/B/C conversation flows | &lt; 3m |
| `ui-messaging` | messaging-critical, group, actions | &lt; 3m |
| `ui-files` | files-export | &lt; 3m |
| `ui-conference` | conference-rtc | &lt; 2m |
| `ui-call-flows` | audio/video call flows with fake media | &lt; 2m |
| `ui-e2ee` | e2ee-browser-roundtrip | &lt; 2m |
| `ui-admin` | admin-console-ui (browser on `:18080/admin/`) | &lt; 1m |
| `ui-admin-extended` | admin manifest smoke, visual surfaces, tablet layout | &lt; 2m |
| `ui-interaction-audit` | aggressive client/admin desktop+mobile interaction audit | &lt; 5m |
| `ui-i18n-artifacts` | visible raw i18n keys/mojibake audit across web/admin UI | &lt; 1m |
| `all-inner` | all inner tiers sequentially | &lt; 10m |
| `full` | outer gate only (`qemu-plan-orchestrator`) | ~5m |

On failure read `deploy/qemu/run/plan-failure-analysis.json`.

## Outer gate (once per fix batch)

```powershell
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp -MaxAcceptanceMinutes 60
```

Requires all inner tiers green unless `-SkipInnerTierCheck`.

## Manual full suite

```powershell
cd tests/e2e-web
npm ci
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
$env:KORUS_API_URL = "http://127.0.0.1:18080"
npx playwright test
```

## Specs (parity-matrix coverage)

| File | Domains |
|------|---------|
| `auth-session.spec.ts` | auth |
| `mobile-shell.spec.ts` | responsive shell, single-pane, thread-back |
| `messaging-visual.spec.ts` | visual guards, screenshots, multi-user thread surfaces |
| `messaging-conversation-flows.spec.ts` | multi-user A/B/C conversation roundtrips |
| `messaging-critical.spec.ts` | chats, messages |
| `messaging-group.spec.ts` | chats (3-user group) |
| `messaging-group-users.spec.ts` | chats (multi-browser A/B/C delivery) |
| `messaging-actions.spec.ts` | messages (send, reply) |
| `files-export.spec.ts` | files, export |
| `contacts-search.spec.ts` | contacts, search |
| `profile-settings.spec.ts` | users, blocks, devices |
| `conference-rtc.spec.ts` | conference, media |
| `call-flows.spec.ts` | audio/video calls, fake media, mocked mesh/LiveKit |
| `e2ee-capabilities.spec.ts` | crypto/e2ee |
| `e2ee-browser-roundtrip.spec.ts` | browser MLS send when `mls_status=active` |
| `admin-console-extended-ui.spec.ts` | admin negative auth, manifest nav, visual/tablet smoke |
| `ui-interaction-audit.spec.ts` | client/admin desktop+mobile buttons, links, fields, layout, and runtime errors |
| `i18n-artifacts.spec.ts` | visible translation artifact audit for web/auth/settings/messaging/admin |

Server guest needs `MLS_STATUS=active` for MLS specs.

Selectors use stable `data-testid` (`auth-submit`, `message-composer`, `call-panel-toggle`, `chat-export-button`) and `#u` / `#p` — not locale-specific button labels.

## Visual guard coverage

`messaging-visual.spec.ts` complements functional DOM checks with viewport screenshots and bounding-box assertions. It does not compare pixels; it catches collapsed or hidden surfaces that can still leave DOM nodes present.

Current visual checks:

- Desktop 3-user delivery: isolated browser contexts for `smoke_user_a/b/c`, A sends once, B and C receive, all three clients keep visible thread/messages/composer surfaces.
- Phone thread: narrow viewport send flow has no horizontal overflow and keeps the thread/composer visible after delivery.

## Conversation flow coverage

`messaging-conversation-flows.spec.ts` covers longer user journeys across isolated browser contexts:

- A sends, B receives, B replies via the UI reply action, A sees the reply.
- 3-user fan-out: A sends to B/C, then B sends to A/C; all three clients see the conversation.
- Late receiver recovery: B opens the chat only after A has sent and still catches up.
- Reaction sync: B reacts, A and C see the reaction after refresh.
- Delete sync: sender deletes, receiver sees the deleted-message state after refresh.

Useful next scenarios to add:

- Multi-user edit sync: A edits, B and C see the updated bubble state. This should wait until the current `messaging-actions.spec.ts` edit case is green.
- Multi-user read state: unread counters and read receipts update across A/B/C.
- Mentions and threads: `@user` mention visibility, mention filters, thread reply excluded from main timeline but visible in the thread UI.
- Files in groups: upload/preview/download state visible for sender and receivers.
- Modal surfaces: forward picker, members modal, settings, poll/schedule/reminder overlays at phone/tablet/desktop.
- E2EE visual states: explicit screenshots for encrypted placeholder vs decrypted preview when MLS is active.

## Call flow coverage

`call-flows.spec.ts` uses fake browser media and mocks transport-heavy dependencies so call UI can be tested on the local QEMU stack without host camera/microphone access.

Current call checks:

- A and B open audio-first mesh call panels in isolated browser contexts with fake media.
- Local call controls toggle microphone, camera, and screen-share UI state.
- Permission-denied `getUserMedia` surfaces an error while keeping the call panel usable.
- LiveKit SFU mode joins through a mocked LiveKit client when the SFU capability is advertised.

What this does not prove:

- Real end-to-end audio is audible to a human.
- Real camera pixels traverse the network.
- Real LiveKit/SFU media tracks are exchanged across a live deployment.

For those, add a QEMU/live-stack SFU tier that asserts remote participants/tracks via LiveKit APIs and WebRTC stats. Real stage/prod acceptance remains deferred in spec 015 until a live host exists.

## Admin UI extended coverage

`admin-console-ui.spec.ts` covers targeted admin workflows. `admin-console-extended-ui.spec.ts` adds broader console health checks:

- Invalid login stays on the login form and shows an auth error.
- Every section from `/api/v1/admin/ui/manifest` can be selected without rendering a blank panel.
- Key panels (`fleet`, `organizations`, `retention`, `legal hold`, `directory sync`) attach viewport screenshots.
- Tablet-width layout keeps header, navigation, and panel visible without horizontal overflow.

The extended smoke intentionally does not execute destructive admin actions. Action-heavy panels should get focused tests with API cleanup/rollback, like the retention policy test.

## Interaction Audit Coverage

`ui-interaction-audit.spec.ts` is an aggressive UI crawler for broad regression discovery. It audits client and admin surfaces in desktop (`1280x900`) and mobile (`390x844`) viewports, clicks safe controls, focuses/fills fields, changes selects, checks horizontal overflow after each action, and fails on page errors or unexpected `console.error`.

The audit writes screenshots and JSON action reports for every surface to `tests/e2e-web/artifacts/ui-interaction-audit/<timestamp>/` and adds `index.json` for review. Set `UI_AUDIT_SCREENSHOT_DIR` to write a run into a specific folder. Destructive and mutation-heavy actions such as logout, delete, purge, retention, admin create/save/apply/rotate/sync/start/stop are skipped or dismissed; add focused tests with cleanup for those behaviors instead of broad crawler clicks.

## Translation Artifact Coverage

`i18n-artifacts.spec.ts` guards against visible localization leaks in both user and admin UI. It scans visible text and accessibility attributes (`aria-label`, `title`, `placeholder`, `alt`, button/input values) for raw keys such as `ui.message.*`, mojibake/replacement characters, and unexpanded `{param}` placeholders.

Current checks:

- Auth shell renders cleanly for all supported locales: `ru`, `en`, `be`, `kk`, `zh`, `ko`.
- Authenticated web UI, chat sidebar/thread, message action buttons, and settings modal have no visible translation artifacts.
- Admin login and key admin panels have no visible raw keys or broken encoding.

Static locale parity remains covered by `node scripts/webui-locale-parity-audit.js`; the Playwright tier validates what actually reaches the browser.

## CI

Optional job in `.github/workflows/deploy-messaging-smoke.yml` (nightly / manual); does not block PR `buildIntegrity`.
