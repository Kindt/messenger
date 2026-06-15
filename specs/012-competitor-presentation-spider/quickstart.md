# Quickstart: Spec 012 — Competitor presentation spider-web

---

## Prerequisites

- Python 3.10+ (repo standard)
- No QEMU / Docker required

---

## Build all HTML (baseline + after implementation)

```powershell
cd D:\proj\korus_messenger
python scripts/build-competitor-comparison-html.py
```

Outputs (repo root):

| File | Purpose |
|------|---------|
| `competitor_comparison.html` | Full deck |
| `competitor_comparison_brief.html` | Sales one-pager |
| `competitor_comparison_segment_bank.html` | Bank / gov |
| `competitor_comparison_segment_industry.html` | Industry |
| `competitor_comparison_segment_cloud.html` | Cloud-first |

Single segment:

```powershell
python scripts/build-competitor-comparison-html.py --segment bank
```

---

## Tests

```powershell
python scripts/test_competitor_products.py
```

After Phase A: tests assert `scenario_fit` on each registry product.

---

## Verify Phase A (v2.9)

1. Open `competitor_comparison.html` — find **11×4 matrix** in Part I.
2. Scroll Enterprise TCO — **SaaS exclusion** callout present.
3. Expand battle cards — Compass, МТС Линк, Loop.
4. Part II — **S-50k** SVG chart.
5. Open segment bank — **ФСТЭК** block; industry — **Compass @10k**.

Check VERSION in HTML footer ≥ `2.9`.

---

## Verify Phase B (v3.0)

- Tier C chart, deployment table, persona paragraphs in segments.
- Methodology header version aligned.

---

## Related docs

- [`design/spider-web-model.md`](design/spider-web-model.md)
- [`contracts/presentation-spider-acceptance-contract.md`](contracts/presentation-spider-acceptance-contract.md)
- [`docs/COMPETITOR_COMPARISON_METHODOLOGY.md`](../../docs/COMPETITOR_COMPARISON_METHODOLOGY.md)
- spec **011** hosted Cells: [`../011-korus-cloud-platform/quickstart.md`](../011-korus-cloud-platform/quickstart.md)
