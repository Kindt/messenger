# Design: Web UI startup fix, i18n, and agent skills

**Date**: 2026-06-14  
**Status**: Validated (brainstorming session)  
**Spec**: `specs/005-webui-i18n-ux/`

## Problem

Symptom A: `http://127.0.0.1:19088/` unavailable (connection aborted). Root cause: web guest Docker build fails — `buildTailwindCss` requires `npm`, absent in `gradle:jdk25-noble` builder image. Containers exit; nothing listens on guest `:9088`.

Separate goal: complete Russian UI via existing `ui-i18n.js` + locale bundles; ~100+ hardcoded strings remain in `app.js`.

## Solution — Docker

Multi-stage build:

1. **tailwind** (`node:20-bookworm-slim`): `npm ci` + `npm run build:css` → `webui/tailwind.css`
2. **builder** (`gradle:jdk25-noble`): copy repo + prebuilt CSS; `gradle distTar -x buildTailwindCss`
3. **runtime** (unchanged): JRE + dist tar

Matches CI intent (npm before Gradle) without bloating final image.

## Solution — i18n

- Default locale **`ru`** (`ui-i18n.js`)
- Locale bundles: **`ru`**, **`en`**, **`be`**, **`kk`**, **`zh`**, **`ko`** (Phase 4)
- All new/changed strings: parallel keys in all six locale files (reference: `ru.js`)
- Replace `"..."` user text in `app.js` with `L("section.key")`
- API errors: keep `localErr()` / `translateError`
- Fallback: active locale → `ru` → key
- Migration batches: Phase 2 (ru/en strings in code) + Phase 4 (regional translations)

## Agent skill stack

| Skill | Role |
|-------|------|
| **korus-webui** | Project UI conventions (this repo) |
| **korus-agent-workflow** | QEMU-only, speckit vs superpowers |
| **superpowers-brainstorming** | UX scope before code |
| **speckit-*** | Tracked feature 005 pipeline |
| **superpowers-systematic-debugging** | Startup/deploy failures |
| **superpowers-test-driven-development** | Playwright-first for behavior |
| **superpowers-verification-before-completion** | Evidence before "done" |
| **canvas** (user-level) | Optional layout mockups |

Install: `.\.cursor\install-superpowers.ps1` (14 superpowers skills). `korus-webui` is committed in repo.

## Verification

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode sync-web
curl.exe -sS -o NUL -w "%{http_code}" http://127.0.0.1:19088/
.\scripts\playwright-dev-loop.ps1 -Tier ui-auth
```

## References

- Guest bootstrap failure log: `/var/log/korus-bootstrap.log` on web VM
- `modules/web-client/webui-build/README.md`
