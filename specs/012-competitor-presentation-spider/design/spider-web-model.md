# Spider-web model — competitor presentation (Spec 012)

**Date:** 2026-06-15  
**Baseline:** v2.8 · 11 products · 5 HTML outputs

---

## Axes

| Axis | Values | v2.8 coverage |
|------|--------|---------------|
| **Products** | 11 (Korus + 10 competitors, tiers A/B/C) | ✅ registry + matrices |
| **Scenarios** | S1 контур+комплаенс · S2 облако · S3 legacy · S4 ценовое давление РФ | ⚠️ частично (decision tree, segments) |
| **Anchors** | S-10k · S-50k · S-100k · E-500k · E-1M | ⚠️ S-50k без отдельного chart |
| **Personas** | ИБ · CFO · CIO · закупка · presales | ⚠️ audience nav, нет extract-блоков |
| **Formats** | full · brief · segment×3 | ✅ 5 HTML |

---

## Gap matrix (priority)

### P0 — v2.9 (must for «ядро паутинки»)

| ID | Gap | Deliverable |
|----|-----|-------------|
| G1 | Нет явной **11×4** product×scenario | `render_product_scenario_matrix_html()` |
| G2 | Enterprise якоря без **«no SaaS compare»** callout | `render_enterprise_saas_callout_html()` |
| G3 | Один battle card (eXpress/Пачка @10k) | + Compass · МТС Линк · Loop @10k |
| G4 | S-50k в таблицах, **нет chart** | `render_fig_tco_s50k_svg()` |
| G5 | Сегменты thin | bank: ФСТЭК row; industry: Compass @10k mini-TCO |

### P1 — v3.0

| ID | Gap | Deliverable |
|----|-----|-------------|
| G6 | Radar только on-prem subset | optional radar all-11 + cloud-tier radar |
| G7 | Tier C без stacked TCO chart | `render_fig_tco_tier_c_svg()` |
| G8 | Нет таблицы deployment models | link spec 011 Cells + on-prem/SaaS/hosted |
| G9 | Methodology v1.5 vs HTML v2.8 drift | sync `COMPETITOR_COMPARISON_METHODOLOGY.md` |
| G10 | Personas только nav | CFO/ИБ one-paragraph extracts per segment |

### P2 — v3.1

| ID | Gap | Deliverable |
|----|-----|-------------|
| G11 | Talk track oral only in reading guide | `competitor_comparison_talktrack.html` or brief appendix |
| G12 | Registry tests не покрывают scenario keys | extend `test_competitor_products.py` |

---

## Non-goals

- Новые продукты вне `registry.json` без product sign-off
- Автоматический парсинг прайсов конкурентов (только ручной registry + sources §)
- QEMU/runtime изменения (static HTML only)

---

## Data flow

```
scripts/competitors/registry.json
        ↓
competitor_comparison_data.py  (render_* + SVG)
        ↓
build-competitor-comparison-html.py  (VERSION bump)
        ↓
competitor_comparison*.html (5 + optional talktrack)
```
