# Инженерный чеклист ИБ (ФСТЭК / подготовка к экспертизе)

Обновляйте статус при каждой итерации [`security-fstec-cycle.md`](../../.cursor/prompts/security-fstec-cycle.md).

**Легенда:** `done` | `partial` | `open` | `n/a` | `deferred`

| ID | Контроль | Реализация / verify | Статус |
|----|----------|---------------------|--------|
| FSTEC-01 | Идентификация и аутентификация (JWT, login) | `AuthResource`, `AuthService` | done |
| FSTEC-02 | Ограничение попыток входа | `AuthRateLimiter`, `smoke-rate-limit.ps1`, `RATE_LIMIT_AUTH_ENABLED` в qemu-regression | partial |
| FSTEC-03 | Разграничение доступа (RBAC) | `@RolesAllowed`, chat membership | done |
| FSTEC-04 | Аудит значимых событий | `AuditPort`, `audit_events` | partial |
| FSTEC-05 | Аудит изменения IP allowlist | `OrgIpAllowlistAdminResource` PATCH → `organization.ip_allowlist.update` | done |
| FSTEC-06 | HTTP security headers | `SecurityHeadersFilter`, `smoke-security-headers.ps1` | done |
| FSTEC-07 | Защита от timing side-channel (exist vs miss) | `TimingSensitivePaths` + `padAuthFailure` + `audit-timing.ps1` | done |
| FSTEC-08 | Org IP allowlist (app layer) | `OrgIpAllowlistFilter`, `smoke-ip-allowlist.ps1` | partial |
| FSTEC-09 | DLP интеграция (lab mock) | `smoke-dlp-mock.ps1`, integrations VM | partial |
| FSTEC-10 | Шифрование локальных секретов (desktop) | `MasterKeyStore`, `smoke-desktop-security.ps1` | done |
| FSTEC-11 | E2EE / MLS (протокол) | `MlsChatFacade`, domain tests | partial |
| FSTEC-12 | Статический анализ (Sonar) | `deploy/sonar-qemu/`, vulnerabilities → 0 | done |
| FSTEC-13 | Модель угроз | [`threat-model-outline.md`](threat-model-outline.md) | partial |
| FSTEC-14 | Журналирование отказов доступа | `ApiDeniedMetrics`, audit | partial |
| FSTEC-15 | TLS / trust store (prod) | deferred LSO-071 | deferred |
| FSTEC-16 | Geo deny policy | backlog G-SEC-05 | deferred |
| FSTEC-17 | Passkeys / WebAuthn | backlog G-SEC-06 | deferred |

## Probe-пути timing normalization (FSTEC-07)

| Endpoint | Класс |
|----------|-------|
| GET `/v1/chats/{id}` | `ChatResource` |
| GET `/v1/users/me`, `/v1/users/{id}` | `UserResource` |
| GET `/v1/chats/{chatId}/messages/{msgId}` | `MessageResource` |
| GET `/v1/files/{fileId}` | `FileResource` |
| POST `/v1/auth/login` | `AuthResource` |

Verify: `.\scripts\audit-timing.ps1 -BaseUrl http://127.0.0.1:18080`

## Gate

```powershell
.\scripts\security-gate.ps1 -Strict
```

## Открытые действия (следующие итерации)

1. FSTEC-02: rate-limit smoke в strict gate (последним в цепочке)
2. FSTEC-04: расширить audit на retention/legal_hold
3. Sonar bugs: 14 (тренд вниз, quality gate OK)
