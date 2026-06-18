# Review Round 1 — Acceptance & visual QA (2026-06-18)

## Automated A0–A10

| ID | Result | Notes |
|----|--------|-------|
| A0 | PASS | `#block-0`, blockers, feature tables |
| A0b | PASS | `honesty_check.py` exit 0 |
| A0c | PASS | User tab jargon denylist clean |
| A0d | PASS | SVG in pm/tech/sales/user tabs |
| A1 | PASS | No external `src=http` |
| A2 | PASS | 4 tabs, ≥16 subsections |
| A3 | PASS | No S-10k/E-1M/KORUS_ANCHORS in data/HTML |
| A3b | PASS | Headroom chips with «без изменения цены/мощностей» |
| A3c | PASS | Schema validation in test_data |
| A3d | PASS | 22 offerings, tier A ≥2 RU rows |
| A3e | PASS | TCO rows link HTTPS source_url |
| A4 | PASS | 4 calculators + pytest |
| A5 | PASS | User wizard/FAQ/tour present |
| A6 | PASS | Partial tags + ops footnotes |
| A7 | PASS | buildIntegrity (see Task 14) |
| A8 | PENDING | Pages enable after push |
| A9 | PASS | `@media (max-width: 375px)` added |
| A10 | PASS | Footer DECK_VERSION, Playwright count |

## Manual checklist

- [x] Block 0 above fold — prototype disclaimer visible
- [x] User §1 readable for non-dev (office language)
- [x] Sales §3 TCO rows have source links
- [x] Headroom chips footnote present
- [x] Mobile 375px — tab buttons full-width stack @640px, tighter padding @375px
- [x] No banned phrases outside block-0 negation

## Findings fixed in R1

1. Added `@media (max-width: 375px)` for A9 (was only 640px).

## Gate

R1 log: **no open findings** → proceed to R2.
