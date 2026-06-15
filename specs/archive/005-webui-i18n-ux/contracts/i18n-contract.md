# Contract: Web UI i18n

## Locale bundles

- **Source (edit)**: `modules/web-client/webui-build/locales/messages/{code}.json`
- **Runtime (fetch)**: `modules/web-client/src/main/resources/webui/locales/{code}.json`
- **Manifest**: `webui/locales/manifest.json` — `{ "default": "ru", "codes": [...] }`
- **Loader**: `ui-i18n.js` — lazy `fetch`, in-memory cache (no `KorusLocales` script tags)
- **Default**: `ru`
- **Build**: `npm run build:locales` in `webui-build` (Gradle task `buildLocales`)

| Code | Language | BCP 47 (html lang) |
|------|----------|-------------------|
| `ru` | Русский | `ru` |
| `en` | English | `en` |
| `be` | Беларуская | `be` |
| `kk` | Қазақша | `kk` |
| `zh` | 中文 (简体) | `zh-Hans` |
| `ko` | 한국어 | `ko` |

## API

- `L(key, params?)` — shorthand in `app.js` → `KorusI18n.t`
- `localErr(raw)` — API/transport errors → current locale (fallback `ru`)
- `localMediaErr(raw)` — getUserMedia errors
- `KorusI18n.init()` — async; loads manifest + default + active locale
- `KorusI18n.setLocale(code)` — returns `Promise`; fetch locale JSON if needed
- `KorusI18n.supportedLocales()` — codes from manifest

## Fallback chain

```
lookup(active locale, key) → lookup(ru, key) → key string
```

## Key naming

```
errors.*  auth.*  settings.*  ws.*  conference.*  media.*  common.*
notifications.*  messages.*  chat.*  files.*  export.*  e2ee.*  time.*  ui.*
```

## Rules

1. Every key in `messages/ru.json` MUST exist in all six locale JSON files.
2. New keys: add to `ru.json` first, propagate in same PR, run `npm run build:locales`.
3. No user-visible literal in `app.js` except `L(...)` and brand names (`Korus`, `Jitsi`, `Keycloak`).
4. Playwright: `data-testid`, `#u`, `#p` — not localized labels.
5. Brand nodes: `translate="no"` where auto-translate would break (guidelines).

## Parity audit

```powershell
node scripts/webui-locale-parity-audit.js
```

```bash
rg '"[А-Яа-яЁё][^"]{3,}"' modules/web-client/src/main/resources/webui/app.js
```

Exceptions: auth error matchers, `WEB_DEVICE_NAME`, technical constants.
