---
name: korus-webui-mobile
description: "Korus webui mobile/responsive UX: CSS breakpoints, single-pane navigation, touch targets, safe-area, Playwright mobile-shell tier, Cursor browser viewport QA. Use when adapting UI for phones/tablets, fixing mobile layout, or maximizing responsive verification."
---

# Korus Web UI — Mobile & Responsive

Project skill for **mobile-first layout** and **maximal verification** of `modules/web-client/src/main/resources/webui/`.

Read **`korus-webui`** first for stack, i18n, QEMU sync, and general UI rules.

## When to use

- User asks: mobile UI, responsive, phone, tablet, narrow viewport, touch, PWA safe-area
- After changing `styles.css`, shell layout in `app.js`, modals, composer, call-panel, auth
- Before claiming «mobile готово» — run verification ladder below

## Skill stack

| Phase | Skill | Purpose |
|-------|-------|---------|
| Entry | **`korus-ui-orchestrator`** | Pipeline + gates (spec 026) |
| Implementation | **`korus-webui`** + this skill | CSS + mobile + i18n |
| Cursor browser QA | user `responsive-testing`, `visual-qa-testing` | `:19088` viewports |
| Automated gate | **`ui-mobile` tier** | `mobile-shell.spec.ts` |
| Done gate | `superpowers-verification-before-completion` | Evidence before «готово» |

## Architecture (mobile shell)

| Mechanism | Where | Rule |
|-----------|-------|------|
| Breakpoint **960px** | `styles.css` `@media (max-width: 960px)` | Single-pane: list **or** thread |
| Breakpoint **520px** | `styles.css` | Compact header; hide `hdr-btn-optional` |
| Class **`has-selection`** | `.messenger` in `renderMain()` | Set when `state.selectedId` — hides sidebar on mobile |
| Back navigation | `[data-testid=thread-back]` | `closeMobileThread()` — clears `selectedId` |
| Call panel | `.messenger-shell.call-open` | Desktop: flex row; mobile: column, panel max 42vh |
| Safe area | `index.html` `viewport-fit=cover`, `env(safe-area-inset-*)` | Header + composer padding |
| iOS zoom | inputs `font-size: 16px` at ≤960px | composer, sidebar search, auth fields |
| Touch | `.msg-actions { opacity: 1 }` on mobile | Actions visible without hover |

**Do not** rely on hover-only affordances on mobile. **Do not** hardcode Russian strings — `L()` + all 6 locales.

## Development workflow

### 1. Plan the screen

For each touched surface, note behavior at:

| Viewport | Width | Expected |
|----------|-------|----------|
| Phone small | 375 | Single pane; no horizontal scroll |
| Phone large | 428 | Same |
| Tablet | 768 | Often still single-pane until >960 |
| Desktop | 1280+ | Sidebar + thread; call panel right |

Full matrix: [VERIFY-CHECKLIST.md](VERIFY-CHECKLIST.md).

### 2. Implement (minimal diff)

1. **CSS first** in `styles.css` — prefer existing breakpoints (960, 520).
2. **JS only when CSS cannot** — e.g. back button, `has-selection`, modal focus trap.
3. New strings → `webui-build/locales/messages/ru.json` → all locales → `npm run build:locales`.
4. Add **`data-testid`** for new mobile-only controls (Playwright + a11y).
5. Rebuild Tailwind only if you added `tw:*` classes in source (`npm run build:css`).

Patterns:

```css
/* Mobile-only control */
.foo-mobile-only { display: none; }
@media (max-width: 960px) {
  .foo-mobile-only { display: inline-flex; }
}
```

```javascript
// Single-pane class
var main = el("div", "messenger" + (state.selectedId ? " has-selection" : ""));
```

### 3. Sync to live UI (QEMU)

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode status
.\scripts\qemu-dev-mode.ps1 -Mode sync-ui    # hotswap webui/
# or -Mode sync-web after Dockerfile change
```

Verify: `http://127.0.0.1:19088/` — **host browser OK** (forwarded port).

## Verification ladder (maximal)

Run **in order**. Stop and fix before the next step if red.

### Step A — Static audit (host, no stack)

```powershell
node scripts/webui-locale-parity-audit.js
```

Grep new UI strings in `app.js` / `styles.css` comments — none hardcoded for users.

### Step B — Cursor browser (manual visual)

Stack up (`qemu-up` / `qemu-stack-wait`). For each viewport in [VERIFY-CHECKLIST.md](VERIFY-CHECKLIST.md):

1. Open `http://127.0.0.1:19088/`
2. Login `csadmin` / `csadmin`
3. Screenshot: auth, chat list, open chat, back, settings modal, call-panel (if touched)
4. Check: **no horizontal scrollbar**, back button works, composer not under home indicator

Use user skills **`responsive-testing`** + **`visual-qa-testing`** (URL = `:19088`, not localhost:3000).

### Step C — Playwright mobile tier (automated)

Requires QEMU + smoke users.

```powershell
.\scripts\playwright-dev-loop.ps1 -Tier ui-mobile
# or with fresh webui:
.\scripts\playwright-dev-loop.ps1 -Tier ui-mobile -SyncWebUi
```

Spec: `tests/e2e-web/specs/mobile-shell.spec.ts` — viewports 390×844 and 768×1024.

Manual run:

```powershell
cd tests/e2e-web
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
$env:KORUS_API_URL = "http://127.0.0.1:18080"
npx playwright test specs/mobile-shell.spec.ts
```

### Step D — Desktop regression

```powershell
.\scripts\playwright-dev-loop.ps1 -Tier ui-auth
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
```

Mobile CSS must **not** break dual-pane at 1280px.

### Step E — Report (required output)

Use template in [VERIFY-CHECKLIST.md](VERIFY-CHECKLIST.md) § Report. Attach: tier result, viewports tested, known gaps.

## Adding mobile coverage for a new feature

1. Extend **`mobile-shell.spec.ts`** or add `feature-mobile.spec.ts` with `test.use({ viewport: { width: 390, height: 844 } })`.
2. Register in `tests/e2e-web/playwright-tiers.json` → tier `ui-mobile` `args`.
3. Selectors: **`data-testid`**, `#u`/`#p` — never button label text.
4. Document new testids in this skill's checklist if they are part of the mobile shell contract.

## Common failures

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Horizontal scroll on phone | Fixed width, `100vw`, wide grid | `min-width: 0`, `max-width: 100%`, audit `.messenger` |
| Thread + list both visible on phone | Missing `has-selection` | Set class when `selectedId` |
| Cannot return to list | No `thread-back` or wrong breakpoint CSS | `data-testid=thread-back`, `@media 960px` |
| iOS zooms on focus | input font-size &lt; 16px | 16px on mobile inputs |
| Actions on messages hidden | hover-only `.msg-actions` | opacity 1 on mobile (see `styles.css`) |
| Playwright green but UI broken | Desktop default viewport only | Run **`ui-mobile`** tier |
| Stale UI on QEMU | Forgot sync | `sync-ui` / `sync-web` |

## Related files

- `modules/web-client/src/main/resources/webui/styles.css` — breakpoints
- `modules/web-client/src/main/resources/webui/app.js` — `renderMain`, `closeMobileThread`
- `tests/e2e-web/specs/mobile-shell.spec.ts` — automated mobile gate
- `tests/e2e-web/fixtures/mobile-ui.ts` — viewport helpers
- `.cursor/skills/korus-webui/SKILL.md` — parent webui skill

## Waves closure (2026-06-20)

| Wave | Scope | Status |
|------|-------|--------|
| 1 | Single-pane `has-selection`, `thread-back`, breakpoints 960/520, call-panel flex | Done |
| 2 | `thread-focus`, i18n `backToChats` / discussion, CSS cascade fix | Done |
| 3 | Compact header, composer/modals/call tests, testids | Done |
| 4 | Auth SE, banners CSS, sidebar scroll/touch | Done |
| Final | D2 wide desktop test, polish, CHANGELOG, tier gate | Done |

**Deferred (not blocking mobile baseline):** Playwright for incoming-call / export-progress banners; auth register-tab UI (depends on `registration_allowed`).

**Regression gate after mobile touch:** `ui-mobile` + `ui-auth` + `ui-messaging` + `ui-conference`.
