# Инженерный чеклист ИБ (ФСТЭК / подготовка к экспертизе)

Обновляйте статус при каждой итерации [`security-fstec-cycle.md`](../../.cursor/prompts/security-fstec-cycle.md).

**Легенда:** `done` | `partial` | `open` | `n/a` | `deferred`

| ID | Контроль | Реализация / verify | Статус |
|----|----------|---------------------|--------|
| FSTEC-01 | Идентификация и аутентификация (JWT, login) | `AuthResource`, `AuthService` | done |
| FSTEC-02 | Ограничение попыток входа | `AuthRateLimiter`, `smoke-rate-limit.ps1`, overlay `RATE_LIMIT_AUTH_ENABLED` | done |
| FSTEC-03 | Разграничение доступа (RBAC) | `@RolesAllowed`, chat membership | done |
| FSTEC-04 | Аудит значимых событий | `AuditPort`, `smoke-admin-audit-retention.ps1` | done |
| FSTEC-05 | Аудит изменения IP allowlist | `organization.ip_allowlist.update` | done |
| FSTEC-06 | HTTP security headers | `SecurityHeadersFilter`, `smoke-security-headers.ps1` | done |
| FSTEC-07 | Защита от timing side-channel | `TimingSensitivePaths`, `audit-timing.ps1` | done |
| FSTEC-08 | Org IP allowlist (app layer) | `OrgIpAllowlistFilter`, hotswap enforce, `smoke-ip-allowlist.ps1` | done |
| FSTEC-09 | DLP интеграция (lab mock) | `smoke-dlp-mock.ps1`; **блокер:** integrations guest `docker compose` build FAIL | partial |
| FSTEC-10 | Шифрование локальных секретов (desktop) | `MasterKeyStore`, `smoke-desktop-security.ps1` | done |
| FSTEC-11 | E2EE / MLS (протокол) | domain tests; live interop — backlog | partial |
| FSTEC-12 | Статический анализ (Sonar) | `deploy/sonar-qemu/`, QG green | done |
| FSTEC-13 | Модель угроз | [`threat-model-outline.md`](threat-model-outline.md) | partial |
| FSTEC-14 | Журналирование отказов доступа | `DeniedAccessAudit`, `smoke-denied-access-audit.ps1` | done |
| FSTEC-15 | TLS / trust store (prod) | ansible + [`fstec-prod-controls.md`](fstec-prod-controls.md), `smoke-fstec-prod-prep.ps1`; live — **LSO-002/007** | partial |
| FSTEC-16 | Geo deny policy | `OrgGeoDenyFilter`, `smoke-org-geo-deny.ps1`; nginx GeoIP на prod | partial |
| FSTEC-17 | Passkeys / WebAuthn | scaffold + `smoke-passkeys-scaffold.ps1`; ceremony — backlog G-SEC-06 | partial |

## Последняя верификация (2026-08-24)

| Шаг | Результат |
|-----|-----------|
| `DeniedAccessAuditTest` | PASS |
| `security-gate -Strict -SkipBuild` | PASS |
| DLP mock | SKIP — integrations bootstrap docker build fail |
| prod eng prep | PASS (`smoke-fstec-prod-prep.ps1`) |

## Gate

```powershell
.\scripts\security-gate.ps1 -Strict
.\scripts\smoke-fstec-prod-prep.ps1
```

## Backlog (вне lab security gate)

1. **FSTEC-09:** починить integrations guest compose build (`connector-runtime`), затем DLP smoke без SKIP
2. **FSTEC-15:** live TLS на stage FQDN — LSO-002 после появления хоста (spec 015)
3. **FSTEC-13 / LSO-071:** STRIDE depth + экспертная сессия ФСТЭК
4. **FSTEC-17:** полный WebAuthn ceremony (G-SEC-06)
