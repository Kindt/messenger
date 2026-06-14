# Plan: 005 Web UI i18n and UX

**Branch**: `005-webui-i18n-ux`  
**Design**: [`docs/plans/2026-06-14-webui-i18n-ux-design.md`](../../docs/plans/2026-06-14-webui-i18n-ux-design.md)

## Phase 0 — Restore startup (P0)

| Task | Owner | Verification |
|------|-------|--------------|
| Multi-stage `Dockerfile.web-client` (Node tailwind + Gradle `-x buildTailwindCss`) | agent | local `docker build -f docker/Dockerfile.web-client .` |
| `qemu-dev-mode -Mode sync-web` | user/agent | `:19088/` 2xx, bootstrap log green |

## Phase 1 — Skills and spec (P1)

| Task | Verification |
|------|--------------|
| `korus-webui` skill in `.cursor/skills/` | SKILL.md references stack |
| Refresh superpowers junctions | `install-superpowers.ps1` |
| `spec.md`, `plan.md`, `tasks.md`, i18n contract | speckit artifacts |

## Phase 2 — i18n migration batches (P1)

Migrate hardcoded strings in `app.js` to `L()` in batches:

1. **Batch A**: errors, auth boot, notifications/push
2. **Batch B**: chat actions (rename, members, ban, saved)
3. **Batch C**: messages, files, export, E2EE labels
4. **Batch D**: conference/RTC, settings panel, document title
5. **Batch E**: `ui-*-utils.js`, `sw.js` offline page (already RU)

After each batch: `playwright-dev-loop -Tier ui-auth` or relevant tier.

## Phase 3 — UX polish (P2, optional)

- Time labels via i18n in `ui-format-utils.js` — **done** (batch E)
- Tailwind coverage for auth/settings shells
- Canvas mockups for layout tweaks (user skill)

## Phase 4 — Multi-locale expansion (P1)

**Supported locales** (default **`ru`**):

| Code | Language | File | Status |
|------|----------|------|--------|
| `ru` | Русский | `locales/ru.js` | complete (reference) |
| `en` | English | `locales/en.js` | complete |
| `be` | Беларуская | `locales/be.js` | translate from `ru` |
| `kk` | Қазақша | `locales/kk.js` | translate from `ru` |
| `zh` | 中文 (简体) | `locales/zh.js` | translate from `en`/`ru` |
| `ko` | 한국어 | `locales/ko.js` | translate from `en`/`ru` |

**Fallback chain** (`ui-i18n.js`): current locale → `ru` → key.

**Batches** (after infra T040):

1. **T041** `be.js` — auth, errors, shell, sidebar, thread (Belarusian)
2. **T042** `kk.js` — same sections (Kazakh)
3. **T043** `zh.js` — full parity (Simplified Chinese)
4. **T044** `ko.js` — full parity (Korean)
5. **T045** Locale parity audit — all keys in `ru.js` exist in every bundle
6. **T046** `ui-format-utils` / `document.documentElement.lang` for `be`, `kk`, `zh-Hans`, `ko`
7. **T047** Playwright smoke: locale switch in settings does not break `#u`/`#p` login

After each language batch: `playwright-dev-loop -Tier ui-auth`.

## Agent skill stack

See `korus-webui` skill — mandatory read for UI work in this repo.

## Constraints

- QEMU-only runtime on Windows host
- Minimal diff per batch
- Playwright selectors unchanged (`data-testid`, `#u`, `#p`)
