# Spec 028 вЂ” Org shell layouts (auth + post-login chrome)

**Feature branch:** `main` (028 без отдельной ветки)  
**Created:** 2026-06-26  
**Status:** **closed** (2026-06-26 — lab/QEMU, layout v1 complete)
**Predecessor:** [`specs/027-ui-branding/`](../027-ui-branding/) вЂ” **closed** (visual skin: palettes, tokens, custom CSS, demo skins)

---

## Problem

Spec 027 closed the **visual skin** track (palette, tokens, CSS, PWA theme). Organizations still need distinguishable **entry and shell layouts** without separate deployable portals:

- Today: single `auth-shell` + standard 3-pane messenger chrome for all orgs.
- `?org_slug=` resolves auth policy (`/auth/login-options`) but **not** public branding or layout.
- Sales/demo want a split auth hero (brand left, form right); some tenants want denser post-login chrome.

Research during 027 closure: **no standalone org portal apps** in repo вЂ” layout belongs in web-client shell, configured per org/platform.

---

## Goal

Configurable **shell layout variant** for:

1. **Pre-login** (`renderAuth`) вЂ” structure and responsive behavior.
2. **Post-login** (main app chrome) вЂ” nav density and optional sidebar collapse defaults.

Optional: resolve branding + layout for anonymous users via `org_slug` (query param or host slug), aligned with existing `AuthPolicyService.resolveOrg`.

---

## Layout variants (v1)

| ID | Name | Auth screen | Post-login |
|----|------|-------------|------------|
| `default` | Standard | Centered card (`auth-shell` today) | Current 3-pane shell |
| `compact` | Compact | Same auth as default | Reduced padding, tighter nav/list rows, smaller title row |
| `auth-split` | Auth split | Two-column: brand/hero panel + form column; stacks on mobile | Reverts to `default` post-login (layout override auth-only unless admin sets both) |

Platform default: `default`. Org may override layout independently of palette (027).

---

## Out of scope

| Item | Reason |
|------|--------|
| Palettes, token overrides, custom CSS, demo skins | **Spec 027 вЂ” closed** |
| Separate subdomain multi-tenant routing / dedicated portal deployables | Future infra (015 ops backlog if prod DNS) |
| Full IA redesign (settings placement, new modules) | Spec 026 orchestrator + product specs |
| White-label native apps | Not web-client |

---

## API sketch

Extend existing branding surface (hex: `UiBrandingPort` / `UiBrandingService`):

| Method | Path | Auth | Change |
|--------|------|------|--------|
| GET | `/api/v1/branding` | Public | Optional `?org_slug=` в†’ merge org branding + **`shell_layout`** |
| GET | `/api/v1/branding/me` | Bearer | Include resolved **`shell_layout`** (org override в†’ platform default) |
| GET/PUT | `/api/v1/admin/branding/platform` | Admin | Field `shell_layout` |
| GET/PUT | `/api/v1/admin/branding/orgs/{orgId}` | Admin | Optional org `shell_layout` override |

Response field (JSON):

```json
{
  "shell_layout": "default",
  "auth_layout": "default",
  "post_login_layout": "default"
}
```

**v1 simplification:** single `shell_layout` enum applied to auth; post-login uses same value except `auth-split` maps post-login to `default`. Phase 2 may split `auth_layout` / `post_login_layout` if product asks.

Public `org_slug` resolution reuses `OrganizationLookupPort.findBySlug` (same as login-options).

---

## DB sketch

Flyway **V076** (proposed; V074-V075 reserved for mesh call recordings):

```sql
ALTER TABLE platform_ui_branding
  ADD COLUMN shell_layout VARCHAR(32) NOT NULL DEFAULT 'default';

ALTER TABLE org_ui_branding
  ADD COLUMN shell_layout VARCHAR(32);  -- NULL = inherit platform
```

Check constraint or app validation: `default`, `compact`, `auth-split`.

Document in [`docs/db/FLYWAY_AND_SCHEMA.md`](../../docs/db/FLYWAY_AND_SCHEMA.md).

---

## Client sketch

- `data-shell-layout` on `document.documentElement` (or `body`) set from branding snapshot.
- CSS: `shell-layouts.css` вЂ” variant modifiers for `.auth-shell`, `.app-shell`, nav/list density.
- `renderAuth()` branches on layout id (extract builders: `renderAuthDefault`, `renderAuthSplit`).
- `refreshBrandingPublic()` passes `org_slug` from URL when fetching `/branding`.
- Admin: dropdown in existing В«РџРµСЂСЃРѕРЅР°Р»РёР·Р°С†РёСЏ UIВ» section (027 panel) вЂ” **layout only**, no palette duplication.

---

## Testing

- H2: adapter read/write `shell_layout`, merge precedence platform в†’ org.
- Unit: layout enum normalization.
- Playwright: `ui-shell-layouts.spec.ts` вЂ” auth-split desktop + mobile stack, compact post-login density smoke.
- Gate: `./gradlew buildIntegrity`.
- QEMU: extend or mirror `run-ui-branding-qemu.ps1` with layout cases on `:19088`.

Live stack: QEMU only (not host Docker). Deferred prod: spec 015.

---

## Relationship to 027

| 027 (closed) | 028 (this spec) |
|--------------|-----------------|
| `palette`, `token_overrides`, `custom_css`, `brand_title`, `demo_skins_enabled` | `shell_layout` (+ optional future split fields) |
| `themes-palettes.css`, `#korus-org-theme` | `shell-layouts.css`, DOM structure in `renderAuth` / app chrome |
| Demo LS palette persistence | Unchanged; layout does not override palette merge rules |

---

## Open decisions (for product / implementer)

**Locked for v1 (2026-06-26, main):**

1. **Single field** `shell_layout` — не разделяем auth/post-login в БД.
2. **Public branding:** `?org_slug=` query only (Host — backlog).
3. **Compact:** post-login density; auth DOM остаётся default, кроме `auth-split`.

Default recommendation: **single field**, **`org_slug` query on public branding**, **compact affects post-login only** when set (auth stays default unless `auth-split`).

---

## Closure notes (2026-06-26)

**Delivered:** V076 `shell_layout`, API merge + `auth_layout`/`post_login_layout` derived fields, public `?org_slug=`, admin dropdown, `shell-layouts.css`, auth-split DOM refactor, compact post-login CSS, Playwright tier `ui-shell-layouts`, QEMU script `run-ui-shell-layouts-qemu.ps1`, plan [`plan.md`](plan.md).

**Lab verify:** `./gradlew buildIntegrity`; live stack `.\scripts\run-ui-shell-layouts-qemu.ps1` (requires guest V076 + web sync).

**Backlog (future specs / ops):** Host header slug routing, separate auth/post-login DB columns, nav IA visibility per org, dedicated portal deployables (spec 015).
