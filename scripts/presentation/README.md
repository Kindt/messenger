# Presentation deck — rebuild runbook

## Rebuild deck (local → GitHub Pages)

```powershell
.\scripts\presentation\publish-deck.ps1
```

Or step by step:

```powershell
python scripts/presentation/build.py
python scripts/presentation/export_gaps_table.py
python scripts/presentation/smoke_deck.py
python scripts/presentation/analyze_deck.py
python scripts/presentation/verify_offerings_urls.py
```

## Tests

```powershell
python -m pytest scripts/presentation/ -q
```

## When to rebuild

- Изменения `FEATURES` / `PRODUCTION_BLOCKERS` в `scripts/presentation/product_status.py`
- Обновление `user_features.py`, `petal_scoring.py`, `data/competitors.json`
- После правок gaps: `python scripts/presentation/export_gaps_table.py` → `gaps_classified.json`
- Обновление `data/competitor_offerings.json` (`source_accessed_at`, `billing_unit`, `tco_comparable`)
- Новый счётчик Playwright
- Константы pricing в `sizing_pricing.py`
- Host colocation / composition presets в `module_sizing.py` (`HOST_LAYOUT_PRESETS`, `COMPOSITION_PRESETS`, `COMPOSE_MEM_LIMIT_GB`)

## Host colocation в калькуляторе

- Колонка **Сервер** у каждого модуля: shared-пул или отдельная VM.
- **Раскладка VM** — пресеты одной кнопкой; **Состав + модель** — addons + host + `planning` / `compose_caps`.
- TCO-таблица/график = prod-full без colocation; интерактивный калькулятор — base + addons + colocation.

См. [`METRIC_POLICY.md`](METRIC_POLICY.md) § Host colocation.

Коммитить вместе: `docs/index.html` + `scripts/presentation/*`.

## Output

- Artifact: `docs/index.html` (self-contained, GitHub Pages `/docs`)
- Live URL: https://kindt.github.io/messenger/ (after Pages enabled — see below)

## GitHub Pages

**Модель:** deck собирается **локально**, в git коммитится `docs/index.html`; workflow только публикует `docs/` (без `build.py` в CI).

1. `.\scripts\presentation\publish-deck.ps1`
2. `git add docs/index.html docs/.nojekyll` (+ при необходимости `scripts/presentation/*`)
3. push в `main` → workflow `Deploy product deck to GitHub Pages` (триггер: изменения в `docs/**`)

**Settings → Pages → Source: GitHub Actions** (или branch `main` / `/docs`).

Live: https://kindt.github.io/messenger/

**CI `buildIntegrity`** на push отключён (только `workflow_dispatch`); gate — локально: `.\gradlew.bat buildIntegrity`.
- Honesty gate: `honesty_check.py` (exit 1 при overclaim)
