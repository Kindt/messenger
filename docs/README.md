# Документация Korus Messenger

**Версия продукта:** `0.0.1-SNAPSHOT` (рабочая болванка, не релиз).

## Для кого что читать

| Аудитория | Документ |
|-----------|----------|
| Новый разработчик | [`../README.md`](../README.md) → [`../AGENTS.md`](../AGENTS.md) |
| Заказчик / presales / команда | **[`index.html`](index.html)** — product deck (GitHub Pages: https://kindt.github.io/messenger/) |
| Архитектура backend | [`ARCHITECTURE_CORE_PACKAGES.md`](ARCHITECTURE_CORE_PACKAGES.md), [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md) |
| Deploy / стенды | [`DEV_STACK_PROFILES.md`](DEV_STACK_PROFILES.md), [`../deploy/ansible/DEPLOY_QUICKSTART.md`](../deploy/ansible/DEPLOY_QUICKSTART.md) |
| Roadmap | [`ROADMAP_EPICS.md`](ROADMAP_EPICS.md), [`plans/README.md`](plans/README.md) |
| QA / acceptance | [`../tests/e2e-web/README.md`](../tests/e2e-web/README.md), [`parity/`](parity/) |

## Product deck (spec 018)

Единственная customer-facing презентация — self-contained [`index.html`](index.html).

```powershell
python scripts/presentation/build.py
python -m pytest scripts/presentation/ -q
```

| Что | Где |
|-----|-----|
| Runbook | [`../scripts/presentation/README.md`](../scripts/presentation/README.md) |
| Статусы Block 0 | [`../scripts/presentation/product_status.py`](../scripts/presentation/product_status.py) |
| Конкуренты / TCO | [`../scripts/presentation/data/competitor_offerings.json`](../scripts/presentation/data/competitor_offerings.json) |
| Политика метрик | [`../scripts/presentation/METRIC_POLICY.md`](../scripts/presentation/METRIC_POLICY.md) |
| Spec | [`../specs/018-product-deck/`](../specs/018-product-deck/) |

## Тематические справочники

| Тема | Файл |
|------|------|
| Ретенция, deep-archive | [`RETENTION_AND_DEEP_ARCHIVE.md`](RETENTION_AND_DEEP_ARCHIVE.md) |
| NATS subjects | [`NATS_SUBJECTS_INTEROP.md`](NATS_SUBJECTS_INTEROP.md) |
| Flyway / схема БД | [`db/FLYWAY_AND_SCHEMA.md`](db/FLYWAY_AND_SCHEMA.md) |
| Порты | [`PORTS_MATRIX.md`](PORTS_MATRIX.md) |
| E2EE | [`E2EE_ARCHITECTURE.md`](E2EE_ARCHITECTURE.md) |
| CI / гигиена репо | [`CI_AND_REPO_HYGIENE.md`](CI_AND_REPO_HYGIENE.md) |
| Контракты acceptance | [`contracts/`](contracts/) |
| Review / sign-off | [`review/`](review/) |

## Журнал и specs

- [`../CHANGELOG.md`](../CHANGELOG.md) — история изменений
- [`../specs/`](../specs/) — spec-kit по фичам
- [`../tz_full.html`](../tz_full.html) — базовое техническое ТЗ (исторический артефакт)
