# Spec 012: Competitor presentation spider-web closure

**Feature branch:** `012-competitor-presentation-spider`  
**Created:** 2026-06-15  
**Status:** `approved`  
**Input:** Gap-анализ «паутинки» после v2.8 — полное покрытие осей product × scenario × anchor × persona × format.

**Design:** [`design/spider-web-model.md`](design/spider-web-model.md)

---

## Goal

Довести генератор сравнительной презентации до состояния, когда **любая комбинация** из реестра 11 продуктов, 4 сценариев и 5 якорей TCO имеет **явный ответ** в HTML (таблица, chart, battle card или честный «не сравниваем») — без дыр для presales на первой встрече.

**Baseline:** v2.8 · `python scripts/build-competitor-comparison-html.py` → 5 HTML.  
**Target versions:** v2.9 (P0) · v3.0 (P1) · v3.1 (P2).

---

## Relationship to other specs

| Spec | Relationship |
|------|--------------|
| **010** | Product presentation §4; multi-stakeholder design; segment HTML referenced in US5 |
| **011** | Hosted Cells (tier B) — deployment table + segment cloud enrichment |
| **013** | Live-streaming (renumbered; unrelated to this spec) |

**Constraint:** только static HTML + Python build; **без** live stack / QEMU для acceptance.

---

## User Scenarios & Testing

### User Story 1 — Product × scenario matrix (Priority: P0)

Как presales, я хочу матрицу **11 продуктов × 4 сценария**, чтобы за 30 секунд понять, кого ставить в шорт-лист для конкретного RFP.

**Independent Test:** rebuild HTML; matrix present in full + brief; each cell = `✓` / `~` / `—` / `Korus` with footnote link.

**Acceptance Scenarios:**

1. **Given** full HTML v2.9+, **When** open Part I, **Then** matrix 11×4 visible after decision tree.
2. **Given** product «VK Teams SaaS», scenario S1 (контур), **When** read cell, **Then** `—` + tooltip «SaaS вне контура».
3. **Given** Korus row, **When** any scenario, **Then** positioning cell links to relevant segment one-pager.

---

### User Story 2 — Enterprise SaaS boundary (Priority: P0)

Как закупка, я хочу явный callout, что **E-500k / E-1M не сравниваются с облачным SaaS per-user**, чтобы не было ложного TCO-аргумента.

**Independent Test:** callout block adjacent to Enterprise TCO section; methodology § mirrors rule.

**Acceptance Scenarios:**

1. **Given** E-500k chart, **When** scroll, **Then** warn box «SaaS не в матрице Enterprise якорей».
2. **Given** brief HTML, **When** Enterprise mention, **Then** one-line disclaimer present.

---

### User Story 3 — Extended battle cards (Priority: P0)

Как sales, я хочу отдельные battle cards против **Compass, МТС Линк, Loop** (и опционально TrueConf), чтобы не импровизировать на возражениях.

**Independent Test:** 3+ new `<details>` or subsection tables @ S-10k anchor; Korus column highlighted (`row-korus`).

**Acceptance Scenarios:**

1. **Given** Compass objection FAQ, **When** expand battle card, **Then** TCO split license+infra @10k.
2. **Given** МТС Линк, **When** read card, **Then** UC-first positioning + Dialog→Линк footnote.
3. **Given** Loop @10k, **When** compare, **Then** Mattermost-fork + cloud per-user noted.

---

### User Story 4 — S-50k visualization (Priority: P0)

Как CFO mid-market, я хочу **stacked TCO chart @50k**, потому что многие RFP фиксируют этот якорь между 10k и 100k.

**Independent Test:** SVG in Part II; values match `KORUS_ANCHORS` / registry; methodology table includes S-50k row (already v1.1+).

**Acceptance Scenarios:**

1. **Given** rebuild, **When** inspect SVG, **Then** Korus + eXpress + Пачka + VK bars @50k.
2. **Given** per-user derived metric, **When** hover/table, **Then** consistent with S-10k/S-100k methodology.

---

### User Story 5 — Segment one-pager enrichment (Priority: P0)

Как ИБ (банк) и CIO (промышленность), я хочу **сегментные дополнения**, не дублируя full deck.

**Independent Test:** segment HTML diff vs v2.8; bank adds ФСТЭК checklist row; industry adds Compass @10k mini-block.

**Acceptance Scenarios:**

1. **Given** `segment_bank.html`, **When** compliance section, **Then** ФСТЭК / реестр row aligned with feature matrix.
2. **Given** `segment_industry.html`, **When** TCO section, **Then** Compass on-prem monthly + infra note @10k.

---

### User Story 6 — Radar & Tier C economics (Priority: P1)

Как аналитик, я хочу radar / TCO для **Tier C** и опционально all-11 radar, чтобы закрыть «российский шорт-лист».

**Independent Test:** new SVG Tier C; radar legend lists МТС Линк, Compass, TrueConf.

---

### User Story 7 — Deployment models table (Priority: P1)

Как architect, я хочу таблицу **on-prem / hosted Cell / SaaS** с ссылкой на spec 011, чтобы связать презентацию с commercial SKU.

**Independent Test:** table in full + segment_cloud; links to `specs/011-korus-cloud-platform/quickstart.md` (relative path in repo docs, public text in HTML).

---

### User Story 8 — Methodology sync (Priority: P1)

Как reviewer, я хочу **methodology.md версии ≥ presentation VERSION**, чтобы audit trail был единым.

**Independent Test:** methodology header version matches or exceeds `VERSION` in build script; changelog entry.

---

### User Story 9 — Persona extracts (Priority: P1)

Как новый presales, я хочу **2–3 предложения под CFO / ИБ / закупку** в каждом segment one-pager.

**Independent Test:** visible blocks with `data-testid` optional; no locale-specific Playwright requirement (static HTML).

---

### User Story 10 — Talk track artifact (Priority: P2)

Как sales lead, я хочу **oral talk track** (5/15/45 min) как отдельный lightweight HTML или brief appendix.

**Independent Test:** file generated; links from reading guide.

---

## Functional Requirements

| ID | Requirement |
|----|-------------|
| FR-012-01 | Matrix 11×4 scenarios in `competitor_comparison_data.py` driven by registry metadata |
| FR-012-02 | Enterprise SaaS exclusion callout in full + brief |
| FR-012-03 | ≥3 new battle card blocks (Compass, МТС Линк, Loop) |
| FR-012-04 | `render_fig_tco_s50k_svg()` wired in full build |
| FR-012-05 | Segment enrichments: bank ФСТЭК, industry Compass @10k |
| FR-012-06 | VERSION bump per phase (2.9 / 3.0 / 3.1) in build script |
| FR-012-07 | `python scripts/test_competitor_products.py` green after registry schema extend |
| FR-012-08 | Methodology + CHANGELOG updated on each version bump |
| FR-012-09 | No hardcoded competitor prices outside registry / `competitor_comparison_data.py` constants |
| FR-012-10 | All new user-facing strings Russian; ASCII in `.py` source |

---

## Success Criteria

| ID | Criterion |
|----|-----------|
| SC-012-01 | Phase A (v2.9): US1–US5 acceptance contract rows green |
| SC-012-02 | Phase B (v3.0): US6–US9 contract rows green |
| SC-012-03 | Phase C (v3.1): US10 optional; no regression in 7 existing unit tests |
| SC-012-04 | Sales sign-off: one internal walkthrough full+brief+3 segments documented in research.md |
| SC-012-05 | Zero «TODO» placeholders in shipped HTML sections marked `req` |

---

## Out of scope

- Playwright tier for static HTML (manual / snapshot diff only)
- Pricing API or automated scrapers
- English locale duplicate of presentation
- Changes to `tz_product_pricing.py` Korus infra formulas (unless anchor bug found)
