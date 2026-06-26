# Spec 028 — Tasks

**Status:** closed (2026-06-26) · **Depends on:** 027 closed (V073 branding tables)

Phases follow speckit: specify (this doc) → plan → implement → verify.

---

## Phase 0 — Specify & plan

| ID | Task | Status |
|----|------|--------|
| OSL-001 | Product sign-off on 3 layout variants + open decisions in `spec.md` | done |
| OSL-002 | `plan.md`: CSS breakpoints, `data-testid` matrix, admin UX wire | done |
| OSL-003 | Contract snippet: branding DTO + admin PUT validation for `shell_layout` | done |

---

## Phase 1 — Backend & DB

| ID | Task | Status |
|----|------|--------|
| OSL-010 | Flyway V076 — `shell_layout` on `platform_ui_branding` / `org_ui_branding` | done |
| OSL-011 | Extend `UiBrandingPort`, JDBC adapter, `UiBrandingService` merge + validation | done |
| OSL-012 | `BrandingResource`: `?org_slug=` on public GET; include `shell_layout` in all branding responses | done |
| OSL-013 | Admin `UiBrandingAdminResource` GET/PUT — platform + org `shell_layout` | done |
| OSL-014 | H2 tests: merge precedence, invalid enum, org_slug public resolve | done |
| OSL-015 | `docs/db/FLYWAY_AND_SCHEMA.md` — V076 row | done |

---

## Phase 2 — Web client (auth + shell)

| ID | Task | Status |
|----|------|--------|
| OSL-020 | `shell-layouts.css` — `default`, `compact`, `auth-split` (responsive) | done |
| OSL-021 | Apply `data-shell-layout` from branding in `ui-branding.js` or shell bootstrap | done |
| OSL-022 | Refactor `renderAuth()` — layout builders; preserve 027 demo skins + brand chrome | done |
| OSL-023 | Post-login compact modifiers on app chrome (nav, lists, title row) | done |
| OSL-024 | `refreshBrandingPublic()` — pass `org_slug` from URL to `/branding` | done |
| OSL-025 | i18n keys for layout labels in admin (6 locales) | done |

---

## Phase 3 — Admin UI

| ID | Task | Status |
|----|------|--------|
| OSL-030 | Admin «Персонализация UI» — layout dropdown + live preview (auth mock) | done |
| OSL-031 | Playwright: admin save platform/org layout, reload client smoke | done |

---

## Phase 4 — QA & closure

| ID | Task | Status |
|----|------|--------|
| OSL-040 | Playwright `ui-shell-layouts.spec.ts` (auth-split, compact, org_slug public branding) | done |
| OSL-041 | Unit tests for layout enum helpers (webui-build) | done |
| OSL-042 | `./gradlew buildIntegrity` green | done |
| OSL-043 | QEMU tier script or extend ui-branding QEMU runner for layout cases | done |
| OSL-044 | Close spec — status + pointer from 027 if needed | done |

---

## Notes

- Visual skin remains in **027** — do not reopen UBR tasks.
- QEMU entry: `scripts/run-ui-shell-layouts-qemu.ps1` (tier `ui-shell-layouts`).
- Backlog (not 028): Host-based `org_slug`, split DB fields auth/post-login, full IA per org.
