# Feature Specification: Web UI i18n and UX Polish

**Feature Branch**: `005-webui-i18n-ux`

**Created**: 2026-06-14

**Status**: Done (2026-06-14)

**Input**: Fix web-client Docker startup (Tailwind/npm in image), restore UI on QEMU :19088, complete Russian i18n for all user-visible strings, improve UI consistency (Tailwind + legacy CSS). **Extend** with full locale set: **ru** (default), **en**, **be**, **kk**, **zh**, **ko**.

**Related specs**: `002-web-client-server-parity`, `004-deferred-phase2-closure`

## Supported locales

| Code | Language | Default |
|------|----------|---------|
| `ru` | Русский | **yes** |
| `en` | English | |
| `be` | Беларуская | |
| `kk` | Қазақша | |
| `zh` | 中文 (简体) | |
| `ko` | 한국어 | |

## User Scenarios & Testing

### User Story 1 — Web stack starts on QEMU (Priority: P0)

As a developer on Windows, I want the web VM to serve UI on `http://127.0.0.1:19088/` after redeploy, so that I can develop and test without host Docker.

**Independent Test**: `curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:19088/` returns 2xx; `tailwind.css` returns 200.

**Acceptance Scenarios**:

1. **Given** fresh `qemu-redeploy -WebOnly`, **When** bootstrap completes, **Then** Docker image builds without `buildTailwindCss` / npm errors.
2. **Given** running web stack, **When** browser opens `/`, **Then** login screen renders with styles (Tailwind + themes).

---

### User Story 2 — Full Russian UI (Priority: P1)

As a Russian-speaking user, I want all labels, errors, prompts, and status messages in Russian by default, so that I can use the product without English fragments.

**Independent Test**: Grep audit — no user-visible hardcoded Cyrillic/Latin UI strings in `app.js` outside `L()` calls; `locales/ru.js` covers all keys used in code.

**Acceptance Scenarios**:

1. **Given** default locale `ru`, **When** user opens settings, auth, chat, conference, **Then** all visible text is Russian (except product names Keycloak/Jitsi where appropriate).
2. **Given** API returns English error message, **When** UI displays error, **Then** `translateError` maps to Russian bundle text.
3. **Given** user switches locale to `en` in settings, **When** UI re-renders, **Then** English bundle is used consistently.

---

### User Story 4 — Regional locales (Priority: P1)

As a user in BY/KZ/CN/KR (or preferring English), I want to switch UI language to Belarusian, Kazakh, Chinese, or Korean, with Russian as the default for new users.

**Independent Test**: Settings shows six locale options; each bundle mirrors the key tree of `ru.js`; missing keys fall back to Russian.

**Acceptance Scenarios**:

1. **Given** first visit (no `localStorage`), **When** app loads, **Then** locale is `ru`.
2. **Given** user selects `zh` in settings, **When** UI re-renders, **Then** translated keys use Chinese; gaps show Russian fallback.
3. **Given** browser language `kk-KZ` and no stored preference, **When** app inits, **Then** `detectLocale()` selects `kk`.

---

### User Story 3 — Maintainable i18n workflow (Priority: P2)

As a maintainer, I want a documented skill stack and spec tasks for UI changes, so that agents and developers follow the same i18n and acceptance path.

**Independent Test**: `.cursor/skills/korus-webui/SKILL.md` exists; `tasks.md` tracks migration batches; inner Playwright tier green after each batch.

**Acceptance Scenarios**:

1. **Given** new UI string, **When** developer adds feature, **Then** keys exist in **all six** locale files (`ru`, `en`, `be`, `kk`, `zh`, `ko`) before merge.
2. **Given** UI-only change, **When** inner loop runs, **Then** relevant Playwright tier passes on QEMU.

---

## Out of scope

- React/Vue migration
- Full visual redesign (logos, marketing)
- Backend API message localization (server `UserMessageSource` — separate effort)

## Success criteria

- [x] UI :19088 healthy on QEMU after web redeploy
- [x] `docker/Dockerfile.web-client` builds in CI and guest VM
- [x] i18n audit: 0 hardcoded user strings in `app.js` (target)
- [x] Playwright `all-inner` tier green on live stack
- [x] Locale bundles: `ru`, `en`, `be`, `kk`, `zh`, `ko` registered; parity audit green
- [x] Settings locale picker lists all six languages with native endonyms
