# Presentation deck — rebuild runbook

## Rebuild deck

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

**Option A (recommended):** Settings → Pages → **Source: GitHub Actions** → workflow `Deploy product deck to GitHub Pages` runs on push to `main`.

**Option B:** Settings → Pages → **Deploy from branch** → `main` / **`/docs`**.

Local rebuild still required before commit when editing data; CI rebuilds on deploy workflow too.
- Honesty gate: `honesty_check.py` (exit 1 при overclaim)
