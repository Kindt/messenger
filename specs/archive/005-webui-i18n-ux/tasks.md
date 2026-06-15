# Tasks: 005 Web UI i18n and UX



## Phase 0 — Startup



- [x] T001 Fix `docker/Dockerfile.web-client` — Node stage for Tailwind, Gradle skips `buildTailwindCss`

- [x] T002 Redeploy web guest: `.\scripts\qemu-dev-mode.ps1 -Mode sync-web`

- [x] T003 Verify `http://127.0.0.1:19088/` and `/tailwind.css` return 2xx



## Phase 1 — Tooling



- [x] T010 Install superpowers junctions (`.\.cursor\install-superpowers.ps1`)

- [x] T011 Add `.cursor/skills/korus-webui/SKILL.md`

- [x] T012 Update `korus-agent-workflow` with webui row

- [x] T013 Create spec/plan/design (`specs/005-*`, `docs/plans/2026-06-14-*`)



## Phase 2 — i18n batches (ru reference + en)



- [x] T020 Batch A: notifications/push, contacts, e2ee → `locales/*.js` + `L()`

- [x] T021 Batch B: group/member admin strings

- [x] T022 Batch C: messages, files, export, storage

- [x] T023 Batch D: modals, settings, thread/composer labels

- [x] T024 Batch E: `ui-format-utils.js` time/TTL via `time.*` + `KorusI18n.t`

- [x] T025 i18n audit — `app.js` grep: only auth error matchers (not UI labels)



## Phase 3 — Acceptance (ru/en milestone)



- [x] T030 Inner tier: `playwright-dev-loop.ps1 -Tier all-inner`

- [x] T031 Update CHANGELOG `[Unreleased]` for Dockerfile + i18n milestone



## Phase 4 — Multi-locale (ru default + en, be, kk, zh, ko)

- [x] T040 Infra: six locales, settings picker, `detectLocale`, `html[lang]`
- [x] T041 Belarusian — `messages/be.json` (full parity)
- [x] T042 Kazakh — `messages/kk.json`
- [x] T043 Chinese — `messages/zh.json`
- [x] T044 Korean — `messages/ko.json`
- [x] T045 Parity audit: `node scripts/webui-locale-parity-audit.js`

## Phase 5 — JSON source, lazy load (no duplicated JS)

- [x] T050 JSON source `webui-build/locales/messages/` + `npm run build:locales`
- [x] T051 Runtime fetch in `ui-i18n.js`; removed legacy `locales/*.js`
- [x] T052 Architecture: `docs/plans/2026-06-14-webui-i18n-json-architecture.md`
- [x] T046 Guidelines: `navigator.languages`, `translate="no"` on brand
- [x] T047 Playwright locale switch + `ui-auth`
- [x] T048 Server `ui_locale` sync (`PATCH /users/me/locale`, login/profile apply)
- [x] T049 Default locale `ru`; regional auto-detect (be/kk/zh/ko only); `sync-api-core` for Java API on QEMU


