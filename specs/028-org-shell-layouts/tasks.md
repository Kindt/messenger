# Spec 028 — Tasks

**Status:** draft · **Depends on:** 027 closed (V073 branding tables)

Phases follow speckit: specify (this doc) → plan → implement → verify.

---

## Phase 0 — Specify & plan

| ID | Task | Status |
|----|------|--------|
| OSL-001 | Product sign-off on 3 layout variants + open decisions in `spec.md` | pending |
| OSL-002 | `plan.md`: CSS breakpoints, `data-testid` matrix, admin UX wire | pending |
| OSL-003 | Contract snippet: branding DTO + admin PUT validation for `shell_layout` | pending |

---

## Phase 1 — Backend & DB

| ID | Task | Status |
|----|------|--------|
| OSL-010 | Flyway V074 — `shell_layout` on `platform_ui_branding` / `org_ui_branding` | pending |
| OSL-011 | Extend `UiBrandingPort`, JDBC adapter, `UiBrandingService` merge + validation | pending |
| OSL-012 | `BrandingResource`: `?org_slug=` on public GET; include `shell_layout` in all branding responses | pending |
| OSL-013 | Admin `UiBrandingAdminResource` GET/PUT — platform + org `shell_layout` | pending |
| OSL-014 | H2 tests: merge precedence, invalid enum, org_slug public resolve | pending |
| OSL-015 | `docs/db/FLYWAY_AND_SCHEMA.md` — V074 row | pending |

---

## Phase 2 — Web client (auth + shell)

| ID | Task | Status |
|----|------|--------|
| OSL-020 | `shell-layouts.css` — `default`, `compact`, `auth-split` (responsive) | pending |
| OSL-021 | Apply `data-shell-layout` from branding in `ui-branding.js` or shell bootstrap | pending |
| OSL-022 | Refactor `renderAuth()` — layout builders; preserve 027 demo skins + brand chrome | pending |
| OSL-023 | Post-login compact modifiers on app chrome (nav, lists, title row) | pending |
| OSL-024 | `refreshBrandingPublic()` — pass `org_slug` from URL to `/branding` | pending |
| OSL-025 | i18n keys for layout labels in admin (6 locales) | pending |

---

## Phase 3 — Admin UI

| ID | Task | Status |
|----|------|--------|
| OSL-030 | Admin «Персонализация UI» — layout dropdown + live preview (auth mock) | pending |
| OSL-031 | Playwright: admin save platform/org layout, reload client smoke | pending |

---

## Phase 4 — QA & closure

| ID | Task | Status |
|----|------|--------|
| OSL-040 | Playwright `ui-shell-layouts.spec.ts` (auth-split, compact, org_slug public branding) | pending |
| OSL-041 | Unit tests for layout enum helpers (webui-build) | pending |
| OSL-042 | `./gradlew buildIntegrity` green | pending |
| OSL-043 | QEMU tier script or extend ui-branding QEMU runner for layout cases | pending |
| OSL-044 | Close spec — status + pointer from 027 if needed | pending |

---

## Notes

- Do **not** reopen 027 palette/demo-skin tasks; reference 027 for visual skin only.
- UI work: run through **`korus-ui-orchestrator`** (spec 026) before `webui/` edits.
- Implementation order: OSL-010…015 → OSL-020…025 → OSL-030 → OSL-040…044.
