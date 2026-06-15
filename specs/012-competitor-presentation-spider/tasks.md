# Tasks: Spec 012 — Competitor presentation spider-web

**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)

---

## Phase A — v2.9 P0

- [ ] T01201 Add `scenario_fit` (S1–S4) to each product in `registry.json`
- [ ] T01202 `render_product_scenario_matrix_html()` + wire full/brief
- [ ] T01203 `render_enterprise_saas_callout_html()` near Enterprise TCO
- [ ] T01204 Battle card: Korus vs Compass @10k (license + infra split)
- [ ] T01205 Battle card: Korus vs МТС Линк (UC-first, Dialog footnote)
- [ ] T01206 Battle card: Korus vs Loop @10k
- [ ] T01207 Optional battle card: TrueConf (UC overlap)
- [ ] T01208 `render_fig_tco_s50k_svg()` + anchor table cross-check
- [ ] T01209 Segment bank: ФСТЭК / реестр compliance block
- [ ] T01210 Segment industry: Compass @10k mini-TCO
- [ ] T01211 Bump `VERSION = "2.9"` in build script
- [ ] T01212 Update CHANGELOG + methodology header (v1.5 → note v2.9 sections)
- [ ] T01213 Extend `test_competitor_products.py` for scenario_fit keys
- [ ] T01214 Rebuild all 5 HTML; manual spot-check matrix + S-50k chart

**Phase A exit:** [`contracts/presentation-spider-acceptance-contract.md`](contracts/presentation-spider-acceptance-contract.md) rows A1–A8.

---

## Phase B — v3.0 P1

- [ ] T01215 `render_fig_tco_tier_c_svg()` (МТС Линк, Compass, TrueConf)
- [ ] T01216 Radar extension: all Tier C labels or separate cloud radar SVG
- [ ] T01217 `render_deployment_models_html()` (on-prem / hosted Cell / SaaS)
- [ ] T01218 Link deployment table to spec 011 positioning (text, no secrets)
- [ ] T01219 Persona extracts: CFO / ИБ / закупка in each segment page
- [ ] T01220 Full methodology sync (`COMPETITOR_COMPARISON_METHODOLOGY.md` v1.6)
- [ ] T01221 Bump VERSION=`3.0`; CHANGELOG
- [ ] T01222 Contract Phase B sign-off row in research.md

---

## Phase C — v3.1 P2

- [ ] T01223 Talk track HTML or brief appendix (5 / 15 / 45 min)
- [ ] T01224 Reading guide link to talk track
- [ ] T01225 Registry test: every product has ≥1 scenario `fit` not all `—`
- [ ] T01226 Bump VERSION=`3.1`; CHANGELOG
- [ ] T01227 Sales walkthrough notes in research.md
- [ ] T01228 Mark spec status `approved` after SC-012-04

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
