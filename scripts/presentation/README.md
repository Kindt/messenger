# Presentation deck — rebuild runbook

## Rebuild deck

```powershell
python scripts/presentation/build.py
python scripts/presentation/smoke_deck.py
python scripts/presentation/analyze_deck.py
```

## Tests

```powershell
python -m pytest scripts/presentation/ -q
```

## When to rebuild

- Изменения `FEATURES` / `PRODUCTION_BLOCKERS` в `scripts/presentation/product_status.py`
- Обновление `competitor_offerings.json` (`source_accessed_at`)
- Новый счётчик Playwright
- Константы pricing в `sizing_pricing.py`

## Commit

Коммитить вместе: `docs/index.html` + `scripts/presentation/*`.

## Output

- Artifact: `docs/index.html` (self-contained, GitHub Pages `/docs`)
- Live URL: https://kindt.github.io/messenger/ (after Pages enabled — see below)

## GitHub Pages

**Option A (recommended):** Settings → Pages → **Source: GitHub Actions** → workflow `Deploy product deck to GitHub Pages` runs on push to `main`.

**Option B:** Settings → Pages → **Deploy from branch** → `main` / **`/docs`**.

Local rebuild still required before commit when editing data; CI rebuilds on deploy workflow too.
- Honesty gate: `honesty_check.py` (exit 1 при overclaim)
