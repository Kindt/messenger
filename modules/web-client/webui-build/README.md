# Web UI — static asset build

Static web client lives in `../src/main/resources/webui/`. Build runs here and emits `tailwind.css`, `styles.css`, `fonts.css`, `fonts/`, `app.bundle.js`, and locale JSON.

## Prerequisites

- Node.js 20+ and npm

## Commands

```bash
cd modules/web-client/webui-build
npm ci
npm run build:assets   # full production pipeline
npm run build:css      # Tailwind only → ../src/main/resources/webui/tailwind.css
npm run build:fonts    # Source Sans 3 woff2 → webui/fonts/ + fonts.css
npm run build:styles   # minify src/styles.css → webui/styles.css
npm run build:js       # esbuild bundle → webui/app.bundle.js (T073/FR-082)

After JS changes run `npm run build:js` (or `build:assets`). `index.html` loads **`app.bundle.js`** instead of ~30 separate modules.

**Load order (T073):**

1. `/web-client-env.js` — sync, from servlet (API/WS URLs)
2. `/korus-mls-wasm.js`, `/e2ee/openmls/korus-openmls-dev.js` — **deferred**, outside bundle (FR-087; large WASM)
3. `/app.bundle.js` — deferred; script list in `scripts/bundle-script-order.mjs`

To add/remove a bundled module: edit `bundle-script-order.mjs`, then `npm run build:js`.
npm run watch:css      # dev loop while editing JS/HTML classes
```

Gradle (from repo root):

```bash
./gradlew :modules:web-client:buildTailwindCss
```

**Edit styles** in `webui-build/src/styles.css` (source), then `npm run build:styles`.

## Usage in vanilla JS

Tailwind **v4** uses colon prefix syntax (`tw:flex`, `tw:md:gap-12`), not v3 dash form (`tw-flex`).

```javascript
el("div", "auth-card tw:w-full tw:max-w-md tw:rounded-2xl tw:p-6 tw:shadow-lg");
```

Playwright selectors (`#u`, `#p`, `data-testid`) stay on legacy classes/ids.

Load order in `index.html`: `fonts.css` → `tailwind.css` → `themes.css` → `styles.css` (legacy component rules override where needed).

## CI

Root `.github/workflows/ci.yml` runs `npm ci`, `build:assets`, and `npm run test:first-load` (SC-024) before `buildIntegrity`.

## Dev HMR / watch (FR-156–159, optional)

- **CSS/Tailwind:** `npm run watch:css` — пересборка `tailwind.css` при правках классов в `webui/*.js` / HTML.
- **JS:** hot reload не встроен — после правок `webui/*.js` выполните `npm run build:js` (или `build:assets`). На QEMU: `scripts/qemu-web-hotswap.ps1`.
- **Live static:** nginx-only volume mount (`korus-web/docker-compose.nginx-only.yml`) или `:modules:web-client:run` для servlet path.
