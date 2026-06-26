# Spec 027 — UI branding (demo skins + org personalization)

**Feature branch:** `027-ui-branding`  
**Created:** 2026-06-26  
**Closed:** 2026-06-26  
**Status:** **closed** (lab/QEMU — visual skin track complete)

---

## Closure notes (2026-06-26)

**Delivered:** demo login palettes (6× light/dark), platform + org branding API (V073), admin «Персонализация UI», client apply (`ui-branding.js`), PWA manifest endpoints, Playwright `ui-branding.spec.ts`, QEMU tier `run-ui-branding-qemu.ps1` (7/7).

**Post-login demo palette:** when platform branding is default `korus` (no org override CSS/tokens/title/logo), logged-in shell keeps demo palette from `localStorage` via `ui-branding.js` `isPlatformDefaultBranding` + `app.js` `applyBrandingConfig` / `resolveMergedPalette`.

**Explicitly out of scope (→ spec 028):** separate org portal URLs, auth/post-login **layout variants** (compact shell, auth-split), public branding resolution by `org_slug` on `/branding`. Research confirmed no standalone org portals exist today — only palette/CSS personalization.

**Known gaps (non-blocking for closure):** uncommitted branding files may exist in working tree; stage/prod branding rollout deferred per spec 015.

---

## Goal

Два трека оформления web-клиента:

| Track | Scope | Persistence |
|-------|--------|-------------|
| **A — Demo** | 6 палитр на экране входа (продажи) | `localStorage` (`korus_web_style.palette`) |
| **B — Admin** | Глобальный default + override организации | Postgres `platform_ui_branding`, `org_ui_branding` |

После входа **серверный** `GET /api/v1/branding/me` имеет приоритет над demo LS. До входа — `GET /api/v1/branding` (public) + demo LS может переопределить **только palette** (не server CSS).

**Палитры:** `korus`, `vtb`, `alfa`, `rzd`, `sfr`, `sberbank`.

---

## API

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/branding` | Public | Platform default + `demo_skins_enabled` |
| GET | `/api/v1/branding/me` | Bearer | Merged org branding for logged-in user |
| GET | `/api/v1/branding/manifest.webmanifest` | Public | PWA manifest (platform theme) |
| GET | `/api/v1/branding/me/manifest.webmanifest` | Bearer | PWA manifest (org merge) |
| GET/PUT | `/api/v1/admin/branding/platform` | Admin | Platform settings |
| GET/PUT | `/api/v1/admin/branding/orgs/{orgId}` | Admin | Org override |

Custom CSS sanitized server-side (`CustomCssSanitizer`).

---

## Client

- CSS: `[data-palette][data-appearance]` in `themes-palettes.css`
- Demo UI: `renderAuth()` → `[data-testid=auth-demo-skins]`, `[data-testid=auth-skin-*]`
- Apply: `ui-branding.js` → `#korus-org-theme`, token overrides, SW `postMessage`
- Feature flag: env `WEB_CLIENT_DEMO_SKINS` + DB `demo_skins_enabled`
- PWA: `sw.js` cache revision bump on branding save

---

## Admin

Section `core-ui-branding` — palette, tokens, custom CSS, live preview, demo flag.

---

## Testing

- H2: `JdbcUiBrandingAdapterH2Test`, `CustomCssSanitizerTest`, `BrandingWebManifestBuilderTest`
- Unit: `test-ui-shell-utils.mjs` (palette LS)
- Playwright: `tests/e2e-web/specs/ui-branding.spec.ts` (9 cases)
- Gate: `./gradlew buildIntegrity`
- QEMU: `scripts/run-ui-branding-qemu.ps1`

Live stack: QEMU `:19088` / `:18080/admin/` only (not host Docker).

Deferred live-server ops: [`specs/015-live-server-ops-backlog/`](../015-live-server-ops-backlog/).
