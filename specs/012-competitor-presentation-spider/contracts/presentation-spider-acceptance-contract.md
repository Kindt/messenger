# Contract: Presentation spider-web acceptance (Spec 012)

**Spec:** [`../spec.md`](../spec.md)  
**Design:** [`../design/spider-web-model.md`](../design/spider-web-model.md)

Evidence = file path + manual check or test log. No QEMU gate.

---

## Phase A — v2.9 P0

| # | Criterion | Evidence |
|---|-----------|----------|
| A1 | Product×scenario matrix 11×4 in full HTML | `competitor_comparison.html` Part I |
| A2 | Matrix summarized or linked in brief | `competitor_comparison_brief.html` |
| A3 | Enterprise SaaS exclusion callout | full § Enterprise TCO + brief disclaimer |
| A4 | Battle cards Compass, МТС Линк, Loop | grep `battle` / product names in HTML |
| A5 | S-50k stacked TCO SVG | `render_fig_tco_s50k_svg` in build output |
| A6 | Segment bank ФСТЭК block | `competitor_comparison_segment_bank.html` |
| A7 | Segment industry Compass @10k | `competitor_comparison_segment_industry.html` |
| A8 | `test_competitor_products.py` green | CI / local pytest log |
| A9 | VERSION ≥ 2.9 in build script | `build-competitor-comparison-html.py` |
| A10 | CHANGELOG [Unreleased] entry | `CHANGELOG.md` |

---

## Phase B — v3.0 P1

| # | Criterion | Evidence |
|---|-----------|----------|
| B1 | Tier C TCO chart | SVG in full HTML Part II |
| B2 | Deployment models table | full + segment_cloud |
| B3 | spec 011 hosted Cell mention | deployment table text |
| B4 | Persona extracts ×3 segments | segment HTML bodies |
| B5 | Methodology version ≥ presentation | `COMPETITOR_COMPARISON_METHODOLOGY.md` header |
| B6 | VERSION ≥ 3.0 | build script |

---

## Phase C — v3.1 P2

| # | Criterion | Evidence |
|---|-----------|----------|
| C1 | Talk track artifact exists | `competitor_comparison_talktrack.html` or brief section |
| C2 | Sales walkthrough logged | `research.md` sign-off section |
| C3 | Spec status `approved` | `spec.md` header |

---

## Regression guard

| # | Criterion | Evidence |
|---|-----------|----------|
| R1 | All 5 baseline HTML still generated | default build command |
| R2 | Existing battle card (eXpress/Пачка) preserved | full HTML Part I |
| R3 | No broken internal links between full/brief/segments | manual click segment links |
| R4 | Registry still 11 products, 18 criteria | `test_competitor_products.py` |

---

## Sign-off roles (SC-012-04)

| Role | Phase | Sign |
|------|-------|------|
| Product / Marketing | A | matrix + battle cards wording |
| Presales lead | A+B | brief usability |
| Finance / CFO reviewer | A | S-50k + Enterprise callout |
| Legal (optional) | A | ФСТЭК wording in bank segment |

Log in [`research.md`](../research.md) § Sign-off.
