# Flyway и схема БД (core-api)

Миграции: `modules/core-api/src/main/resources/db/migration/`.

До первого релиза допустим wipe lab/QEMU и правка baseline вместо длинных backfill-цепочек (см. `.cursor/rules/hex-legacy-deprecation.mdc`).

## Журнал значимых миграций

| Version | File | Назначение |
|---------|------|------------|
| V073 | `V073__ui_branding.sql` | **UI branding (spec 027):** `platform_ui_branding` (global palette, token_overrides, custom_css, brand_title, demo_skins_enabled, revision), `org_ui_branding` (per-org override). Seed row `id=1`, palette `korus`. |

### V073 — детали

- **platform_ui_branding** — одна строка (`id=1`), флаг `demo_skins_enabled` для demo-кнопок на login.
- **org_ui_branding** — optional override по `org_id` → FK `organizations(id) ON DELETE CASCADE`.
- **API:** `GET /api/v1/branding`, `GET /api/v1/branding/me`, admin ` /api/v1/admin/branding/*`.
- **PWA:** `GET /api/v1/branding/manifest.webmanifest` (public), `GET /api/v1/branding/me/manifest.webmanifest` (Bearer).

Lab: после pull с V073 — guest `qemu-sync-api-core -NoCache` или redeploy server.
