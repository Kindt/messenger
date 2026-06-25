---
name: korus-webui
description: "Korus Messenger web UI stack reference (vanilla JS, Tailwind tw:*, i18n L(), bundle). Use AFTER korus-ui-orchestrator routes to ENGINEER — not as first UI skill."
---

# Korus Web UI

Project-specific skill for `modules/web-client/src/main/resources/webui/`.

## When to use

- **Orchestrator already classified** and state = ENGINEER (or P1 brief path)
- Web client does not load, blank screen, broken styles (after INTAKE)
- Russification / i18n (`locales/{ru,en,be,kk,zh,ko}.js`, `ui-i18n.js`; default **`ru`**)
- UI layout, Tailwind classes, settings, conference panel
- Docker web-client image build failures (`buildTailwindCss`, npm)
- Playwright inner loop for UI tiers

## Agent workflow

For multi-step UI work read **`korus-ui-orchestrator`** first (spec `specs/026-cursor-ui-agent-orchestrator/`).

This skill is the **engineer domain reference** (stack, i18n, runtime, checklist) after orchestrator reaches ENGINEER phase.

## Architecture (do not fight it)

- **No React/Vue** — `app.js` + `ui-*.js` modules → production **`app.bundle.js`** (esbuild)
- **After JS edits:** `cd webui-build && npm run build:js` (see [`gaps-quickref`](../korus-ui-orchestrator/gaps-quickref.md))
- **New bundled module:** `webui-build/scripts/bundle-script-order.mjs` + `build:js`
- **Lazy call code:** `ui-call-mesh.js`, `ui-call-livekit.js` via `ui-lazy-call.mjs` (not in main bundle)
- **E2EE wasm:** `korus-mls-wasm.js`, `e2ee/openmls/*` — outside bundle, load before bundle
- **i18n**: `L("section.key")` via `ui-i18n.js`; messages in `webui-build/locales/messages/*.json` → `npm run build:locales`
- **Errors from API**: `localErr(msg)` / `KorusI18n.translateError`
- **Tailwind v4**: `tw:flex`, `tw:md:gap-4` (not `tw-flex`)
- **CSS source:** `webui-build/src/styles.css` → `npm run build:styles` → `webui/styles.css`
- **CSS load order** (`index.html`): `fonts.css` → `tailwind.css` → `styles.css` → `themes.css`
- **Addon gating:** `isPlatformAddonEnabled("addon-*")` — UI may be hidden in lab
- **Playwright:** `#u`, `#p`, `data-testid=*`
- **Build dir:** `modules/web-client/webui-build/`

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
5. Grep audit hardcoded user strings in `app.js` and touched `ui-*.js` — migrate to `L()`

## UI change checklist

1. Edit **`webui-build/locales/messages/ru.json`**, propagate to other `messages/*.json`
2. Run **`npm run build:locales`**
3. **`node scripts/webui-label-lint.js`** before merge (parity + iconBtn lint; CI: `./gradlew checkWebuiLabelLint`)
4. If new Tailwind: **`npm run build:css`**
5. If **`ui-*.js` / `app.js`:** **`npm run build:js`** before `sync-ui`
6. If **`webui-build/src/styles.css`:** **`npm run build:styles`**
7. Playwright: `playwright-dev-loop.ps1 -Tier <ui-*>`; mobile: **`-Tier ui-mobile`**
8. Update spec task checkbox when applicable
9. Notable behavior: `[Unreleased]` in `CHANGELOG.md`

## Docker web-client image

`docker/Dockerfile.web-client` must build Tailwind **before** Gradle (Node stage) or install npm in builder. Failure symptom: guest bootstrap `buildTailwindCss FAILED` / `command 'npm'` / UI :19088 connection aborted.

## Related docs

- `specs/archive/005-webui-i18n-ux/` — archived; living: `docs/plans/2026-06-14-webui-i18n-json-architecture.md`
- `docs/plans/2026-06-14-webui-i18n-ux-design.md` — validated design
- `modules/web-client/webui-build/README.md` — Tailwind commands
- `.cursor/skills/korus-webui-mobile/SKILL.md` — mobile/responsive UX + verification ladder
- `.cursor/skills/korus-ui-orchestrator/gaps-quickref.md` — bundle, CSS, addons
