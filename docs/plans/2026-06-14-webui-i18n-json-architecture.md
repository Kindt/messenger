# Architecture: Web UI i18n without locale duplication

**Date**: 2026-06-14  
**Spec**: `specs/005-webui-i18n-ux` Phase 5  
**Guidelines**: [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines) — section **Locale & i18n**

## Problem

Six `locales/*.js` files duplicated the same key tree (~428 keys × 6). Every new string required editing six JS modules; `index.html` loaded **all** locales up front (~90 KiB).

## Target architecture

```
webui-build/locales/messages/   ← source of truth (edit here)
  ru.json, en.json, be.json, kk.json, zh.json, ko.json

npm run build:locales           ← parity check + copy

webui/locales/                  ← runtime artifacts (committed for hotswap)
  manifest.json
  {code}.json

ui-i18n.js                      ← fetch + cache active locale (+ ru fallback)
app.js                          ← L("key") unchanged
```

### Principles

| Principle | Implementation |
|-----------|----------------|
| **Single source per language** | One JSON file per locale, no nested JS IIFE |
| **No structural duplication** | Key tree defined once per language; parity audit vs `ru.json` |
| **Lazy load** | Only `manifest.json` + default `ru.json` + active locale fetched |
| **Fallback** | `active → ru → key` (unchanged) |
| **Default `ru`** | `manifest.default`, `localStorage`, then `navigator.language` |
| **No app bundler** | Stays vanilla JS; build step only for Tailwind + locale copy |
| **QEMU hotswap** | Edit `messages/*.json` → `npm run build:locales` → `sync-ui` |

## Web Interface Guidelines alignment

| Rule | Status / action |
|------|-----------------|
| Dates/times via `Intl.*` | `ui-format-utils.js` uses `toLocaleString` + BCP 47 tag |
| Numbers via `Intl.*` | TTL/counts use i18n strings; extend if numeric UI grows |
| Language from `navigator.languages` | `detectLocale()` uses `navigator.language`; optional: prefer `languages[]` |
| Brand/code `translate="no"` | Use on product title nodes (`Korus`, `Keycloak`, `Jitsi`) |
| Loading copy ends with `…` | Locale strings use `…` (audit in JSON) |
| Performance | One locale JSON ~15–20 KiB vs six JS at startup |

## Developer workflow

```powershell
# Edit translations
code modules/web-client/webui-build/locales/messages/be.json

# Parity + copy to webui
cd modules/web-client/webui-build
npm run build:locales

# Audit (CI-friendly)
node scripts/webui-locale-parity-audit.js

# Deploy to guest
..\..\..\scripts\qemu-dev-mode.ps1 -Mode sync-ui
```

Gradle `:modules:web-client:processResources` depends on `buildTailwindCss` + `buildLocales`.

## Migration notes

- Legacy `locales/*.js` removed; one-time extract: `node webui-build/scripts/extract-locales-from-js.mjs` (from git history if needed).
- `ui-i18n.init()` is **async**; `boot()` waits before first render.
- `setLocale(code)` returns `Promise`; settings picker awaits before re-render.
- Service Worker precaches `manifest.json` + `ru.json` (v6 cache).

## Out of scope (future)

- Shared keys with backend `UserMessageSource` / `.properties`
- TMS export (PO/Gettext)
- Splitting `app.js` into ES modules + bundler
