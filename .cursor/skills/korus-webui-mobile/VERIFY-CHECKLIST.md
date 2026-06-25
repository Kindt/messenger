# Mobile verification checklist — Korus webui

Use with skill **`korus-webui-mobile`**. Check every item that applies to your change before «готово».

**Status (2026-06-20):** waves 1–4 **closed** — baseline responsive shell shipped; gate **`ui-mobile`** (13 specs). Deferred: E2E for incoming-call / export banners (RTC/export setup).

## Viewport matrix

| ID | Size | Device ref | Gate |
|----|------|------------|------|
| M1 | 375 × 667 | iPhone SE | `mobile auth @ phone SE` |
| M2 | 390 × 844 | iPhone 13/14 | `mobile shell @ phone` |
| M3 | 428 × 926 | iPhone Pro Max | CSS shared with M2 |
| T1 | 768 × 1024 | iPad portrait | `mobile shell @ tablet` |
| D1 | 1280 × 800 | Laptop | `mobile shell @ desktop` |
| D2 | 1920 × 1080 | Full HD | `mobile shell @ desktop wide` |

**H-scroll test:** `document.documentElement.scrollWidth <= window.innerWidth + 1` (`expectNoHorizontalScroll`)

## Screen checklist (baseline — done in waves 1–4)

### Auth — covered by SE + phone tests

### Chat list — sidebar tabs/folders test

### Thread — back, compact header, composer, forward, members

### Modals — settings, members, forward

### Call panel — phone stack + desktop beside + wide D2

### Banners — CSS only (no automated E2E)

## `data-testid` contract (mobile shell)

| testid | Surface |
|--------|---------|
| `thread-back` | Return to chat list (mobile) |
| `call-panel-toggle` | Open/close call panel |
| `call-panel-title` | Call panel visible |
| `settings-toggle` | Settings modal |
| `chat-members-button` | Group members modal |
| `members-overlay` / `members-close` | Members modal |
| `forward-overlay` / `forward-cancel` | Forward picker |
| `message-composer` | Composer textarea |
| `sidebar-tab-*` / `sidebar-folder-*` | Sidebar navigation |

## Automated tiers (closure gate)

```powershell
.\scripts\playwright-dev-loop.ps1 -Tier ui-mobile      # 13 tests
.\scripts\playwright-dev-loop.ps1 -Tier ui-auth
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
.\scripts\playwright-dev-loop.ps1 -Tier ui-conference
```

## Report template

```markdown
## Mobile verification

**Change:** [one line]

**Viewports (manual):** M1 ☐ M2 ☐ M3 ☐ T1 ☐ D1 ☐ D2 ☐

**Automated:**
- ui-mobile: PASS / FAIL
- ui-auth: PASS / FAIL
- ui-messaging: PASS / FAIL
- ui-conference: PASS / FAIL

**Issues found:** [none | list]

**Known gaps / follow-up:** [optional]
```
