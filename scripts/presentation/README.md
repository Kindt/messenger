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

- Изменения `FEATURES` / `PRODUCTION_BLOCKERS` в `product_status.py`
- Обновление `competitor_offerings.json` (`source_accessed_at`)
- Новый счётчик Playwright
- Константы pricing в `sizing_pricing.py`

## Commit

Коммитить вместе: `docs/index.html` + `scripts/presentation/*`.

## Output

- Artifact: `docs/index.html` (self-contained, GitHub Pages `/docs`)
- Honesty gate: `honesty_check.py` (exit 1 при overclaim)
