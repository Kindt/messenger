# Plan 018 — Product Deck

## Architecture

```
scripts/presentation/
  data/          competitors.json, competitor_offerings.json, schema
  product_status.py, sizing_pricing.py, compare_engine.py
  content.py → marketing.py → visuals.py → render.py
  honesty_check.py, build.py, smoke_deck.py, analyze_deck.py
docs/index.html  (generated, self-contained)
```

## Pipeline

1. Persona draft (`content.py`) from facts
2. Layout wrap (`marketing.py`) — no new copy
3. SVG (`visuals.py`) from engine data
4. `honesty_check.py` — exit 1 on overclaim
5. `build.py` → `docs/index.html`

## Compare policy

- One row per `competitor_offering` with `metric=registered_users`
- TCO only if `price_is_public` and HTTPS `source_url`
- Headroom badge when profile max > competitor RU

## Canonical plan

[`docs/plans/2026-06-18-four-tab-presentation-deck.md`](../../docs/plans/2026-06-18-four-tab-presentation-deck.md)

## Verification

```powershell
python scripts/presentation/build.py
python scripts/presentation/smoke_deck.py
python scripts/presentation/analyze_deck.py
python -m pytest scripts/presentation/ -q
./gradlew buildIntegrity
```
