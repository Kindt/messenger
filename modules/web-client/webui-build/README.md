# Web UI — Tailwind CSS build

Static web client lives in `../src/main/resources/webui/`. Tailwind is built here and emitted as `tailwind.css` next to `styles.css`.

## Prerequisites

- Node.js 20+ and npm

## Commands

```bash
cd modules/web-client/webui-build
npm ci
npm run build:css    # one-shot → ../src/main/resources/webui/tailwind.css
npm run watch:css    # dev loop while editing JS/HTML classes
```

Gradle (from repo root):

```bash
./gradlew :modules:web-client:buildTailwindCss
```

## Usage in vanilla JS

Tailwind **v4** uses colon prefix syntax (`tw:flex`, `tw:md:gap-12`), not v3 dash form (`tw-flex`).

```javascript
el("div", "auth-card tw:w-full tw:max-w-md tw:rounded-2xl tw:p-6 tw:shadow-lg");
```

Playwright selectors (`#u`, `#p`, `data-testid`) stay on legacy classes/ids.

Load order in `index.html`: `tailwind.css` → `themes.css` → `styles.css` (legacy component rules override where needed).

## CI

Root `.github/workflows/ci.yml` runs `npm ci` in this folder before `buildIntegrity`.
