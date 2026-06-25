# Settings IA inventory (living map)

Sync with `app.js` `SETTINGS_TAB_IDS` and `appendSettings*Panel`.  
UX Evaluator axis **C** — сверка с [`ux-evaluation-framework.md`](../../../specs/026-cursor-ui-agent-orchestrator/design/ux-evaluation-framework.md).

**Last updated:** 2026-06-25 (Phase 1.9)

| Tab | Content (functions / testids) | IA status |
|-----|------------------------------|-----------|
| `general` | theme, locale, cache, PWA, offline clear, API version, kbd hint | ✅ |
| `profile` | presence select, custom status, display name, blocked users; DND hint → notifications | ✅ |
| `notifications` | push/sound toggles, test push, devices, **DND duration** (`settings-dnd-duration`), **reminders** (`settings-reminders`), **scheduled** (`settings-scheduled`) | ✅ |
| `links` | public links list, refresh | ✅ |
| `security` | E2EE keys, local key IO, **federation directory** (`federation-settings-panel`), passkeys, SIP | ✅ |

## Cross-tab flows

| User action | Tab A | Tab B |
|-------------|-------|-------|
| Set presence DND | profile: select `dnd` | notifications: duration + until hint |
| Manage reminders | — | notifications only |

## Known debt (watch)

| ID | Issue | Status |
|----|-------|--------|
| — | Federation `trust_level` | ✅ G36 i18n keys |
| — | offline clear iconBtn | ✅ G35 |

## Evaluator P5 prompt examples

- «Всё ли на своих вкладках settings?» → walk this table
- «DND настроен правильно?» → profile + notifications cross-tab
