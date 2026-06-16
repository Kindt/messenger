# Tasks: Spec 012 — Competitor presentation spider-web

**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)

---

## Phase A — v2.9 P0

- [x] T01201 Add `scenario_fit` (S1–S4) to each product in `registry.json`
- [x] T01202 `render_product_scenario_matrix_html()` + wire full/brief
- [x] T01203 `render_enterprise_saas_callout_html()` near Enterprise TCO
- [x] T01204 Battle card: Korus vs Compass @10k (license + infra split)
- [x] T01205 Battle card: Korus vs МТС Линк (UC-first, Dialog footnote)
- [x] T01206 Battle card: Korus vs Loop @10k
- [x] T01207 Optional battle card: TrueConf (UC overlap)
- [x] T01208 `render_fig_tco_s50k_svg()` + anchor table cross-check
- [x] T01209 Segment bank: ФСТЭК / реестр compliance block
- [x] T01210 Segment industry: Compass @10k mini-TCO
- [x] T01211 Bump `VERSION = "2.9"` in build script
- [x] T01212 Update CHANGELOG + methodology header (v1.5 → note v2.9 sections)
- [x] T01213 Extend `test_competitor_products.py` for scenario_fit keys
- [x] T01214 Rebuild all 5 HTML; manual spot-check matrix + S-50k chart

**Phase A exit:** [`contracts/presentation-spider-acceptance-contract.md`](contracts/presentation-spider-acceptance-contract.md) rows A1–A8.

---

## Phase B — v3.0 P1

- [x] T01215 `render_fig_tco_tier_c_svg()` (МТС Линк, Compass, TrueConf)
- [x] T01216 Radar extension: all Tier C labels or separate cloud radar SVG
- [x] T01217 `render_deployment_models_html()` (on-prem / hosted Cell / SaaS)
- [x] T01218 Link deployment table to spec 011 positioning (text, no secrets)
- [x] T01219 Persona extracts: CFO / ИБ / закупка in each segment page
- [x] T01220 Full methodology sync (`COMPETITOR_COMPARISON_METHODOLOGY.md` v1.6)
- [x] T01221 Bump VERSION=`3.0`; CHANGELOG
- [x] T01222 Contract Phase B sign-off row in research.md

---

## Phase C — v3.1 P2

- [x] T01223 Talk track HTML or brief appendix (5 / 15 / 45 min)
- [x] T01224 Reading guide link to talk track
- [x] T01225 Registry test: every product has ≥1 scenario `fit` not all `—`
- [x] T01226 Bump VERSION=`3.1`; CHANGELOG
- [x] T01227 Sales walkthrough notes in research.md
- [x] T01228 Mark spec status `approved` after SC-012-04

---

## Dependencies

| Task | Blocked by |
|------|------------|
| T01217 deployment table | spec 011 Phase 0 scaffold (manifest example exists) |
| T01209 ФСТЭК | product/legal confirmation of wording (draft OK with «в процессе») |

---

## Parallelization

- T01204–T01207 battle cards — parallel after T01201 registry
- T01209–T01210 segments — parallel with T01202 matrix
- Phase B can start before Phase A sales sign-off if P0 HTML already reviewed informally
