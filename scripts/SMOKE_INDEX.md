# Smoke Scripts Index

Единый индекс smoke-сценариев для docs/CI. Массовые удаления скриптов делать только после миграции всех ссылок из:

- `.github/workflows/*.yml`
- `README.md`
- `docs/CI_AND_REPO_HYGIENE.md`
- `scripts/TEST_SERVER_READY.md`

Актуальные порты окружений: `docs/PORTS_MATRIX.md`.

## Port-sensitive smoke defaults

- `scripts/smoke-push-worker.sh` / `.ps1`:
  - `full-server`: `http://localhost:9194/health`
  - `dev-min --profile web`: `http://localhost:9193/health`
  - по умолчанию скрипты пробуют оба порта.
- `scripts/smoke-export-worker-metrics.sh` / `.ps1`: `http://localhost:9193/metrics` (export-replay).
- `scripts/smoke-retention-worker.ps1`: `http://localhost:9192/health` (retention).

## Canonical сценарии (использовать по умолчанию)

| Сценарий | Canonical script | CI usage | Параллельные обертки |
|---|---|---|---|
| Export compliance flow | `scripts/smoke-export-compliance-flow.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd`, `smoke-export-compliance-with-file-flow.*` |
| Export compliance pack | `scripts/smoke-export-compliance-pack.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd` |
| Export observability | `scripts/smoke-export-observability.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd` |
| OpenAPI export compliance | `scripts/smoke-openapi-export-compliance.sh` | `export-compliance-smoke.yml` | `.ps1` |
| Korus web basic smoke | `scripts/smoke-korus-web.sh` | manual | `.ps1`, `.cmd` |
| Auth smoke | `scripts/smoke-auth.ps1` | manual | none |
| Stack readiness smoke | `scripts/smoke-ready.ps1` | manual | none |
| Retention worker health smoke | `scripts/smoke-retention-worker.ps1` | manual | none |
| US2 Epic01 (QEMU wrapper) | `scripts/smoke-us2-epic01-qemu.ps1` | manual | `smoke-us2-epic01.ps1` |
| Hot-plug indexer lifecycle | `scripts/smoke-hotplug-indexer.ps1` | manual | requires NATS (`14222` tunnel on QEMU) |

## Operator utilities

- `scripts/stop-local-indexer.ps1` — stop orphan `:services:indexer:run` after smoke/manual runs (Windows).
- `scripts/publish-spec-001-branch.ps1` — push branch `001-system-review-refactoring` when GitHub is reachable.
- `scripts/apply-hotplug-signoff.ps1` — record ADR/constitution approvals (T048/T056).

## Export / retention extended scenarios

Используются для точечных проверок, не являются обязательными в CI по умолчанию:

- `scripts/smoke-export-chat.sh` / `.ps1`
- `scripts/smoke-export-chat-cancel.sh` / `.ps1`
- `scripts/smoke-export-chat-request-cancel.sh` / `.ps1`
- `scripts/smoke-export-suggested.sh` / `.ps1`
- `scripts/smoke-export-suggested-nats.sh` / `.ps1`
- `scripts/smoke-export-suggest-flow.sh` / `.ps1`
- `scripts/smoke-export-suggest-cancel-flow.sh` / `.ps1`
- `scripts/smoke-export-auto-queue-nats.sh` / `.ps1`
- `scripts/smoke-retention-export-suggested.sh` / `.ps1`
- `scripts/smoke-retention-export-suggested-full.ps1`
- `scripts/smoke-deep-archive-chunks.ps1`
- `scripts/smoke-ttl-ui.ps1`
- `scripts/smoke-export-worker-metrics.sh` / `.ps1`
- `scripts/smoke-admin-export.sh` / `.ps1`
- `scripts/smoke-admin-export-cancel.sh` / `.ps1`
- `scripts/smoke-admin-export-request-cancel.sh` / `.ps1`
- `scripts/smoke-admin-export-download.sh` / `.ps1`
- `scripts/smoke-admin-export-inspect.sh` / `.ps1`
- `scripts/smoke-admin-export-global-jobs.sh` / `.ps1`
- `scripts/smoke-admin-export-compliance-prep.sh` / `.ps1`
- `scripts/smoke-export-compliance-stack.sh` / `.ps1`
- `scripts/smoke-export-compliance-with-file-flow.sh` / `.ps1` / `.cmd`

## Deprecation policy

- До отдельного cleanup PR ничего не удалять.
- Если сценарий дублируется (`.sh`, `.ps1`, `.cmd`), canonical для CI/документации выбирается в пользу `.sh`.
- `.ps1` и `.cmd` остаются для Windows-операторов до явной миграции.
