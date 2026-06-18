# Документация Korus Messenger

**Версия продукта:** `0.0.1-SNAPSHOT` (рабочая болванка, не релиз).

## Для кого что читать

| Аудитория | Документ |
|-----------|----------|
| Новый разработчик | [`../README.md`](../README.md) → [`../AGENTS.md`](../AGENTS.md) |
| Заказчик / presales | **[`index.html`](index.html)** (GitHub Pages: https://kindt.github.io/messenger/) |
| Детальный продуктовый текст | [`PRODUCT_PRESENTATION.md`](PRODUCT_PRESENTATION.md) (исходник, legacy) |
| Архитектура backend | [`ARCHITECTURE_CORE_PACKAGES.md`](ARCHITECTURE_CORE_PACKAGES.md), [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md) |
| Deploy / стенды | [`DEV_STACK_PROFILES.md`](DEV_STACK_PROFILES.md), [`../deploy/qemu/README.md`](../deploy/qemu/README.md), [`../deploy/ansible/DEPLOY_QUICKSTART.md`](../deploy/ansible/DEPLOY_QUICKSTART.md) |
| Roadmap | [`ROADMAP_EPICS.md`](ROADMAP_EPICS.md), [`plans/README.md`](plans/README.md) |
| QA / acceptance | [`../tests/e2e-web/README.md`](../tests/e2e-web/README.md), [`parity/`](parity/) |

## Презентация продукта (spec 018)

**Каноническая публикация** — self-contained deck [`index.html`](index.html):

```powershell
python scripts/presentation/build.py
python -m pytest scripts/presentation/ -q
```

Runbook: [`../scripts/presentation/README.md`](../scripts/presentation/README.md).

### Legacy HTML (корень репозитория)

| Файл | Назначение | Пересборка |
|------|------------|------------|
| [`../product_presentation.html`](../product_presentation.html) | Линейная презентация §1–18 (старый формат) | `python scripts/build-tz-product-html.py` |
| [`../competitor_comparison*.html`](../competitor_comparison_brief.html) | Матрицы конкурентов | `python scripts/build-competitor-comparison-html.py` |
| [`../tz_product.html`](../tz_product.html) | Редirect → deck | — |

Источник статусов для legacy-сборок: [`../scripts/product_status.py`](../scripts/product_status.py).  
Для deck: [`../scripts/presentation/product_status.py`](../scripts/presentation/product_status.py).

> Новые материалы для заказчика — только через deck pipeline (`scripts/presentation/`). Legacy не удаляем без явного решения; не дублировать контент в корень.

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
- [`../specs/`](../specs/) — spec-kit: spec / plan / tasks / contracts по фичам
