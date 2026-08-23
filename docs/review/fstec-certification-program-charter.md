# Программа подготовки к сертификации ФСТЭК (инженерный устав)

**Продукт:** Korus Messenger `0.0.1-SNAPSHOT`  
**Статус:** инженерная подготовка; формальная экспертиза — backlog LSO-071 (spec 015, live-server)

## Цель

Свести реализацию и доказательную базу к требованиям прикладной ИБ для корпоративного мессенджера: идентификация/аутентификация, разграничение доступа, аудит, защита каналов и данных, устойчивость к типовым атакам.

## Область

| Компонент | Путь | Примечание |
|-----------|------|------------|
| Core API | `modules/core-api` | JWT, RBAC, audit, headers, rate limit |
| Web UI | `webui/` | CSP, сессия, E2EE UX |
| Desktop SDK | `modules/desktop-client-sdk` | шифрование локальных секретов |
| Integrations | `integrations/` | DLP mock, plugin bridge |
| Lab verify | QEMU `127.0.0.1:18080` / `:19088` | не stage/prod |

## Роли

| Роль | Ответственность |
|------|-----------------|
| Разработка | закрытие пунктов `fstec-engineering-checklist.md` |
| ИБ / архитектор | `threat-model-outline.md`, приёмка контролей |
| QA | `security-gate.ps1 -Strict`, VPP security gates |
| Эксперт ФСТЭК (внешний) | итоговая оценка — после LSO-071 |

## Конвейер (цикл)

Промпт: [`.cursor/prompts/security-fstec-cycle.md`](../../.cursor/prompts/security-fstec-cycle.md)

```powershell
.\scripts\security-gate.ps1 -Strict
.\deploy\sonar-qemu\sonar-scan.ps1 -SkipCompile -OnHost   # при стабильной sonar-lab VM
```

## Артефакты трассируемости

- [`fstec-engineering-checklist.md`](fstec-engineering-checklist.md) — статус контролей
- [`threat-model-outline.md`](threat-model-outline.md) — угрозы и меры
- [`../desktop/FSTEC_SECURITY_MATRIX.md`](../desktop/FSTEC_SECURITY_MATRIX.md) — desktop
- [`../SECURITY_AUDIT.md`](../SECURITY_AUDIT.md) — timing audit
- `deploy/sonar-qemu/run/issues.json` — статический анализ

## Ограничения

- До Sep 2026+ нет реального stage/prod — lab QEMU только
- Сертификация ФСТЭК не заменяется автоматическими скриптами
- Коммиты — по явной просьбе заказчика
