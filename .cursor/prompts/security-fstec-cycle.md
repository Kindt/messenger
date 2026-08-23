# Цикл: безопасность и ФСТЭК (конвейер)

Скопируй всё ниже. Опционально укажи область: `core-api`, `desktop`, `integrations`, `docs/review`.

---

## Цель

Довести **инженерные контроли ИБ** до максимально возможной оценки для подготовки к экспертизе ФСТЭК: трассируемость требований, автоматические гейты, устранение находок Sonar/security smokes.

**Не заменяет** сертификацию ФСТЭК и экспертную оценку (LSO-071 в spec 015 — live-server, отложено).

## Артефакты (обновлять при каждой итерации)

| Артефакт | Путь |
|----------|------|
| Чеклист ИБ | `docs/review/fstec-engineering-checklist.md` |
| Устав программы | `docs/review/fstec-certification-program-charter.md` |
| Модель угроз (outline) | `docs/review/threat-model-outline.md` |
| Матрица desktop | `docs/desktop/FSTEC_SECURITY_MATRIX.md` |
| Timing audit | `docs/SECURITY_AUDIT.md` (из `audit-timing.ps1`) |
| Sonar issues | `deploy/sonar-qemu/run/issues.json` |

## Конвейер (одна итерация)

1. **Baseline** — `.\scripts\security-gate.ps1 -SkipQemuSmokes` (или `-SkipBuild` если стек недоступен)
2. **Stack** — при необходимости `.\scripts\Start-KorusServerStack.ps1 -Mode warm` (WHPX, не host Docker)
3. **Strict gate** — `.\scripts\security-gate.ps1 -Strict`  
   Включает: headers, timing, rate-limit, IP allowlist API, DLP mock (skip если integrations VM down), desktop security matrix + SDK tests
4. **Sonar** (если VM стабильна, без korus-server+web одновременно):
   ```powershell
   .\deploy\sonar-qemu\qemu-up.ps1 -KeepDisk
   .\deploy\sonar-qemu\sonar-scan.ps1 -SkipCompile -OnHost
   .\deploy\sonar-qemu\fetch-issues.ps1
   ```
5. **Diagnose** — bugs/vulnerabilities Sonar, FAIL smokes, пункты чеклиста со статусом `open`
6. **Fix** — минимальный diff (1–3 файла за итерацию):
   - timing normalization: `TimingSensitivePaths` на probe-пути
   - audit: `AuditPort.record` на admin/policy изменения
   - headers, rate limit, IP allowlist, DLP bridge
7. **Verify** — изолированный smoke или `:modules:core-api:test` по затронутому модулю
8. **Traceability** — отметить закрытые пункты в `fstec-engineering-checklist.md`
9. **Repeat** — следующий открытый пункт; не перезапускать весь Sonar при каждом мелком фиксе

## Приоритет закрытия (макс. инженерная оценка)

| P | Контроль | Проверка |
|---|----------|----------|
| P0 | Сборка + unit security | `buildIntegrity`, `smoke-desktop-security` |
| P0 | HTTP security headers | `smoke-security-headers.ps1` |
| P0 | Timing side-channels (exist vs miss) | `audit-timing.ps1` → delta ≤ threshold |
| P1 | Rate limiting login/API | `smoke-rate-limit.ps1` |
| P1 | Org IP allowlist + audit | `smoke-ip-allowlist.ps1`, `audit_events` |
| P1 | DLP plugin bridge (lab) | `smoke-dlp-mock.ps1` |
| P2 | Sonar vulnerabilities/bugs | `deploy/sonar-qemu/run/issues.json` |
| P2 | Документация угроз/чеклист | `docs/review/*` |
| P3 | Geo deny, passkeys WebAuthn | backlog G-SEC-05/06 |

## Запрещено

- Объявлять «готово к ФСТЭК» без strict gate + актуального чеклиста
- Host Docker для runtime
- TCG / `KORUS_QEMU_FORCE_TCG`
- Массовый рефактор вне security scope
- Коммит без явной просьбы пользователя

## Успех итерации

- `security-gate.ps1 -Strict` PASS **или** задокументированный blocker с планом
- Чеклист: ≥1 пункт `open` → `done` с ссылкой на verify
- Sonar: vulnerabilities = 0 (цель); bugs — тренд вниз

## Связанные промпты

- Сборка: `build-until-green.md`
- Полная приёмка: `vpp-until-green.md`
- Pre-merge: `branch-review-cycle.md`
