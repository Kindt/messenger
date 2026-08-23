# Матрица безопасности desktop-клиента (ФСТЭК)

Требуется для `scripts/smoke-desktop-security.ps1`.

## Контроли SDK

| ID | Контроль | Реализация | Тест |
|----|----------|------------|------|
| DSK-01 | Шифрование master key (AES-GCM) | `MasterKeyStore` | `UpdateServiceTest.aesGcmRoundTrip` |
| DSK-02 | Платформенное хранилище токенов | `PlatformSecureTokenStore` | `UpdateServiceTest.platformSecureTokenStorePersistsEncrypted` |
| DSK-03 | Self-check максимального grade | `SecuritySelfCheck` | `UpdateServiceTest.securitySelfCheckMaximumGrade` |
| DSK-04 | TLS trust (lab self-signed) | `HttpClientFactory` | `@SuppressWarnings(S4830)` + lab-only |
| DSK-05 | OkHttp изолирован от desktop-client | `UpdateService.withDefaultClient()` | compile boundary |

## Verify

```powershell
.\scripts\smoke-desktop-security.ps1
```

## Ограничения lab

- Trust-all / self-signed только для dev/QEMU; prod — certificate pinning (backlog).

## Трассируемость

- Чеклист: [`../review/fstec-engineering-checklist.md`](../review/fstec-engineering-checklist.md) FSTEC-10
