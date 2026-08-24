# Модель угроз (outline) — Korus Messenger

Краткий outline для согласования с экспертом ФСТЭК. Детализация STRIDE/PASTA — при формальной экспертизе (LSO-071).

## Активы

| Актив | Описание |
|-------|----------|
| Учётные данные | пароли (hash), JWT, refresh tokens |
| Сообщения | plaintext server-side (non-E2EE), MLS ciphertext (E2EE) |
| Файлы | blob storage, метаданные |
| Аудит | `audit_events`, admin actions, `access.*.denied` |
| Конфигурация | org policies, IP allowlist, geo deny, retention |

## Границы доверия

```
[Client: web / desktop / mobile]
        │ TLS (prod) / lab HTTP
        ▼
[Core API + WS Gateway]
        │ JDBC / Redis / S3
        ▼
[Postgres, Redis, MinIO, Keycloak]
```

## Угрозы и меры (выборка)

| Угроза | Вектор | Мера | Статус |
|--------|--------|------|--------|
| T1 Перебор паролей | POST `/auth/login` | rate limit, timing pad | done |
| T2 Утечка по timing (exist user/chat) | GET API | `TimingSensitivePaths`, `audit-timing.ps1` | done |
| T3 IDOR (чужой chat/file) | REST | membership checks, 403/404, `DeniedAccessAudit` | done |
| T4 XSS в web UI | браузер | CSP headers, sanitization | partial |
| T5 CSRF | cookie session | JWT bearer (stateless API) | n/a API |
| T6 Подмена admin policy | REST admin | `@RolesAllowed("admin")`, audit | done |
| T7 Обход сетевой политики | corp network | org IP allowlist + audit deny | done |
| T8 Утечка через DLP bypass | message send | plugin bridge verdict | partial (lab mock) |
| T9 Компрометация локального desktop | disk | AES-GCM master key | done |
| T10 MITM (prod) | сеть | TLS ansible, `smoke-tls-redirect.ps1` | partial (eng ready) |
| T11 Geo policy bypass | CDN/proxy | `OrgGeoDenyFilter`, nginx GeoIP header | partial (scaffold) |
| T12 Credential phishing | login | passkeys scaffold; full WebAuthn — backlog | partial |

## Verify

- Автоматические: `security-gate.ps1 -Strict`, `audit-timing.ps1`, `smoke-fstec-prod-prep.ps1`
- Ручные: penetration test на stage (после Sep 2026+)

## Связанные документы

- [`fstec-engineering-checklist.md`](fstec-engineering-checklist.md)
- [`fstec-prod-controls.md`](fstec-prod-controls.md)
- [`../SECURITY_AUDIT.md`](../SECURITY_AUDIT.md)
