# Spec 028 — Implementation plan (org shell layouts)

**Status:** complete with implementation (2026-06-26)  
**Spec:** [`spec.md`](spec.md) · **Tasks:** [`tasks.md`](tasks.md)

---

## Layout model (v1)

| `shell_layout` (DB) | `auth_layout` (derived) | `post_login_layout` (derived) | DOM / CSS |
|---------------------|-------------------------|-------------------------------|-----------|
| `default` | `default` | `default` | Centered `.auth-shell` + standard 3-pane messenger |
| `compact` | `default` | `compact` | Auth unchanged; `html[data-shell-layout=compact]` tightens nav/lists/header |
| `auth-split` | `auth-split` | `default` | `.auth-shell-split` grid: `.auth-split-hero` + `.auth-split-card` |

Merge: org `shell_layout` → platform default. Public `GET /branding?org_slug=` uses `OrganizationLookupPort.findBySlug`.

---

## CSS breakpoints

| Breakpoint | Behavior |
|------------|----------|
| `> 960px` | `auth-split`: two columns (hero + card) |
| `≤ 960px` | `auth-split`: single column stack; hero centered |
| all | `compact`: post-login only via `html[data-shell-layout=compact]` selectors |

Files: `modules/web-client/src/main/resources/webui/shell-layouts.css` (imported from `themes.css`).

---

## Client apply order

1. Boot / login: `refreshBrandingPublic()` → `?org_slug=` from URL when present.
2. `applyBrandingConfig()` → `uiBranding.applyOrgBranding()` (027 skin) + `applyShellLayout({ postLogin: !!state.tokens })`.
3. `renderAuth()` reads `state.branding.auth_layout` → `renderAuth()` split vs default builders.

Attributes on `<html>`:

- `data-shell-layout` — active layout for current phase (auth or post-login).
- `data-shell-layout-source` — raw merged `shell_layout` from API.

---

## `data-testid` matrix

| testid / selector | Surface | When |
|-------------------|---------|------|
| `auth-shell` | Login root | Always on auth |
| `auth-split-hero` | Split hero column | `auth_layout=auth-split` |
| `auth-submit` | Login form | Password auth enabled |
| `html[data-shell-layout]` | Global | After branding apply |
| `html[data-shell-layout-source]` | Global | When API returned `shell_layout` |
| `admin-branding-toolbar` | Admin | Section core-ui-branding |
| `#brandingShellLayout` | Admin | Layout dropdown |
| `admin-branding-save` | Admin | Persist platform/org branding |

Playwright tier: `ui-shell-layouts` → `specs/ui-shell-layouts.spec.ts`.

---

## Admin UX wire

Section **core-ui-branding** (027 panel extended):

```
[Scope: Global | Organization] [Palette ▼] [Shell layout ▼] [Brand title]
[Token overrides grid]
[Custom CSS textarea]
[☑ demo_skins_enabled]  (global only)
[Save] [Reset] [Preview]
```

- **Shell layout** dropdown: `default`, `compact`, `auth-split`.
- **Preview** sets `data-palette`, `data-shell-layout`, tokens, CSS on admin `<html>` (not full auth mock iframe).
- **Organization** scope: requires UUID in global org bar; org row may override `shell_layout` only.

API: `GET/PUT /api/v1/admin/branding/platform|orgs/{id}` with `shell_layout` field.

---

## QEMU / lab

| Step | Command |
|------|---------|
| Stack | `.\scripts\qemu-up.ps1` |
| API + V076 | `.\scripts\qemu-sync-api-core.ps1 -NoCache` + poll guest job |
| Web bundle | `.\scripts\qemu-web-sync.ps1` |
| Tier | `.\scripts\run-ui-shell-layouts-qemu.ps1` |

URLs: API `127.0.0.1:18080`, UI `127.0.0.1:19088`. Dev org slug: `dev` (`V042` seed).

---

## Out of scope (unchanged)

Visual skin (027), host-based slug, separate portal deployables, IA/tab visibility, native apps — see [`spec.md`](spec.md) § Out of scope.
