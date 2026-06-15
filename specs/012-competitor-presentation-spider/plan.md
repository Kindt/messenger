# Plan: Spec 012 — Competitor presentation spider-web

**Spec:** [`spec.md`](spec.md)  
**Design:** [`design/spider-web-model.md`](design/spider-web-model.md)  
**Date:** 2026-06-15  
**Status:** `draft`

---

## Architecture summary

| Layer | Deliverable | Repo touchpoints |
|-------|-------------|------------------|
| **Registry** | scenario_fit per product | `scripts/competitors/registry.json` |
| **Render** | new `render_*` blocks | `scripts/competitor_comparison_data.py` |
| **Build** | VERSION + section order | `scripts/build-competitor-comparison-html.py` |
| **Segments** | enriched bodies | `SEGMENT_SPECS` in data module |
| **Docs** | methodology sync | `docs/COMPETITOR_COMPARISON_METHODOLOGY.md` |
| **Tests** | registry + render smoke | `scripts/test_competitor_products.py` |

**No runtime:** acceptance = rebuild HTML + unit tests + manual sales review.

---

## Phase A — v2.9 P0 (1–2 sessions)

**Goal:** US1–US5 — core spider gaps.

| Workstream | Deliverable |
|------------|-------------|
| Matrix | `render_product_scenario_matrix_html()` + registry keys |
| Enterprise | `render_enterprise_saas_callout_html()` |
| Battle cards | Compass, МТС Линк, Loop (+ TrueConf optional) |
| Chart | `render_fig_tco_s50k_svg()` |
| Segments | bank ФСТЭК, industry Compass @10k |

**Exit:** contract Phase A rows; VERSION=`2.9`; CHANGELOG entry.

---

## Phase B — v3.0 P1 (1 session)

**Goal:** US6–US9 — analyst + architect depth.

| Workstream | Deliverable |
|------------|-------------|
| Tier C | stacked TCO SVG + table snippet |
| Radar | extend `render_fig_onprem_radar_svg` or add cloud radar |
| Deployment | table on-prem / hosted Cell / SaaS → spec 011 |
| Methodology | bump to v1.6+ aligned with v3.0 |
| Personas | CFO / ИБ / закупка extracts in 3 segments |

**Exit:** contract Phase B rows; VERSION=`3.0`.

---

## Phase C — v3.1 P2 (optional)

**Goal:** US10 talk track + test coverage for scenario metadata.

| Workstream | Deliverable |
|------------|-------------|
| Talk track | `competitor_comparison_talktrack.html` or brief section |
| Tests | scenario_fit validation in registry tests |

**Exit:** VERSION=`3.1`; sales walkthrough logged in research.md.

---

## Risks

| Risk | Mitigation |
|------|------------|
| Stale competitor prices | sources § + «as of» date in hero |
| HTML size bloat | battle cards in `<details>`; brief stays lean |
| spec 011 drift | deployment table links to manifest example, not live Cells |

---

## Verification commands

```powershell
python scripts/test_competitor_products.py
python scripts/build-competitor-comparison-html.py
# optional: diff competitor_comparison.html vs git baseline
```
