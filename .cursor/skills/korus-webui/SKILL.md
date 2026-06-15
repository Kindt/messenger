---
name: korus-webui
description: "Korus Messenger web UI (vanilla JS webui/): Tailwind v4 tw:*, i18n L(), Playwright data-testid, QEMU :19088 acceptance. Use for UI bugs, russification, UX, and Dockerfile web-client builds."
---

# Korus Web UI

Project-specific skill for `modules/web-client/src/main/resources/webui/`.

## When to use

- Web client does not load, blank screen, broken styles
- Russification / i18n (`locales/{ru,en,be,kk,zh,ko}.js`, `ui-i18n.js`; default **`ru`**)
- UI layout, Tailwind classes, settings, conference panel
- Docker web-client image build failures (`buildTailwindCss`, npm)
- Playwright inner loop for UI tiers

## Skill stack (read in order)

| Phase | Skill | Purpose |
|-------|-------|---------|
| Design / scope | `superpowers-brainstorming` | UX and i18n scope before code |
| Tracked feature | `speckit-specify` -> `speckit-plan` -> `speckit-tasks` -> `speckit-implement` | Spec **005-webui-i18n-ux** |
| Bridge / constraints | `korus-agent-workflow` | QEMU-only runtime, Russian comms |
| Startup / deploy fail | `superpowers-systematic-debugging` | Root cause (guest bootstrap, Docker, ports) |
| New UI behavior | `superpowers-test-driven-development` | Playwright spec first when behavior changes |
| Before "done" | `superpowers-verification-before-completion` | curl :19088, tier green |
| Interactive mockups | User skill `canvas` (~/.cursor/skills-cursor/canvas/) | Layout prototypes only, not production code |

## Architecture (do not fight it)

- **No React/Vue** — single `app.js` + helper modules (`ui-*-utils.js`)
- **i18n**: `L("section.key")` via `ui-i18n.js`; default locale **`ru`**
- **Errors from API**: `localErr(msg)` / `KorusI18n.translateError` — never hardcode English server messages in UI
- **Tailwind v4**: prefixed utilities `tw:flex`, `tw:md:gap-4` (not `tw-flex`)
- **CSS load order** (`index.html`): `tailwind.css` -> `themes.css` -> `styles.css`
- **Playwright selectors**: `#u`, `#p`, `data-testid=*` — not locale-specific labels
- **Build**: `modules/web-client/webui-build/` (npm) -> `webui/tailwind.css`; Gradle `buildTailwindCss`

## Dev runtime (Windows host)

**Forbidden on host**: `docker compose`, host `:modules:web-client:run` against live stack.

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode status
.\scripts\qemu-dev-mode.ps1 -Mode sync-web      # redeploy web guest after Dockerfile/webui change
.\scripts\qemu-dev-mode.ps1 -Mode sync-ui       # hotswap webui only (after enable-hotswap)
.\scripts\playwright-dev-loop.ps1 -Tier ui-auth # inner acceptance
```

Verify: `http://127.0.0.1:19088/`, API `http://127.0.0.1:18080/api/v1/health`.

## i18n rules

1. All user-visible strings go to **all six** locale files (`ru`, `en`, `be`, `kk`, `zh`, `ko`); `ru.js` is the reference key tree.
2. Use `L("key", { param })` in `app.js` and utils; `{param}` interpolation in bundles.
3. `window.prompt` / `confirm` texts — also via `L()`.
4. Brand names (`Korus`, `Jitsi`) may stay Latin; surrounding UI must be Russian by default.
5. Grep audit: `"[А-Яа-яЁё]` and `"[A-Z][a-z]+ [a-z]` in `app.js` — migrate to locale keys.

## UI change checklist

1. Edit **`webui-build/locales/messages/ru.json`**, propagate to other `messages/*.json`.
2. Run **`npm run build:locales`** (in `webui-build`).
3. **`node scripts/webui-locale-parity-audit.js`** before merge.
2. Replace hardcoded string with `L(...)` in JS
3. If new Tailwind classes: `cd modules/web-client/webui-build && npm run build:css` (or Gradle build)
4. Inner tier: `playwright-dev-loop.ps1 -Tier <ui-*|all-inner>`
5. Update `specs/009-platform-modules/tasks.md` checkbox when applicable
6. Notable behavior: `[Unreleased]` in `CHANGELOG.md`

## Docker web-client image

`docker/Dockerfile.web-client` must build Tailwind **before** Gradle (Node stage) or install npm in builder. Failure symptom: guest bootstrap `buildTailwindCss FAILED` / `command 'npm'` / UI :19088 connection aborted.

## Related docs

- `specs/archive/005-webui-i18n-ux/` — archived; living: `docs/plans/2026-06-14-webui-i18n-json-architecture.md`
- `docs/plans/2026-06-14-webui-i18n-ux-design.md` — validated design
- `modules/web-client/webui-build/README.md` — Tailwind commands
- `tests/e2e-web/playwright-tiers.json` — tier manifest
