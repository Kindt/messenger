# Research: Spec 012 — Competitor presentation spider-web

**Date:** 2026-06-15

---

## Decision log

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Spec **012** = presentation spider; live-streaming → **013** | User request «spec 012» for presentation; avoid slug collision |
| D2 | Phased VERSION 2.9 → 3.0 → 3.1 | Matches P0/P1/P2 from gap analysis; minimal big-bang diff |
| D3 | scenario_fit in registry.json | Single source for 11×4 matrix; testable |
| D4 | Enterprise = no SaaS TCO compare | Methodology floor rule; prevents false CFO charts |
| D5 | Battle cards @ S-10k anchor | Existing battle card baseline; Compass public 490 ₽/mo reference |
| D6 | Static HTML only | No Playwright tier; aligns with sales artifact nature |
| D7 | Deployment table waits on spec 011 manifest example | Phase B; text link only in HTML |

---

## Sources (competitor pricing — verify on КП date)

| Product | Source | Notes |
|---------|--------|-------|
| eXpress | express.ms, docs.express.ms | 3000 ₽/user/year corporate |
| Пачка | pachca.ru | corp per-user monthly |
| VK Teams | vk.com/business | SaaS tiers |
| Loop | loop.ru/pricing | cloud 119–199 ₽/user/mo |
| Compass | getcompass.ru/pricing | 390/490 ₽/mo tiers |
| МТС Линк | mts-link.ru | КП-only for enterprise |
| TrueConf | trueconf.ru | server licensing |

Full list in HTML `render_sources_html()` — keep in sync on price updates.

---

## Prior art in repo

| Artifact | Relevance |
|----------|-----------|
| `docs/plans/2026-06-15-competitor-presentation-redesign-design.md` | v2.0 redesign (done) |
| `docs/COMPETITOR_COMPARISON_METHODOLOGY.md` v1.5 | floors, anchors |
| v2.6–v2.8 CHANGELOG entries | battle card, segments, reading guide |
| Gap analysis 2026-06-15 (chat) | P0/P1 list for this spec |

---

## Sign-off

| Role | Date | Phase | Notes |
|------|------|-------|-------|
| Engineering | 2026-06-16 | A | scenario matrix, battle cards, S-50k, segments — tests green |
| Engineering | 2026-06-16 | B | tier C chart, deployment table, personas, methodology v1.6 — tests green |
| Engineering | 2026-06-16 | C | talk track HTML, scenario_fit test, VERSION 3.1 |
| Product / Marketing | — | A | pending |
| Presales lead | — | A+B | pending |
| — | — | C | pending |

---

## Open questions

1. **TrueConf battle card** — P0 optional or defer to P1 if HTML length concern?
2. **ФСТЭК wording** — «в процессе» vs target certification date (product input).
3. **Talk track** — separate HTML (`competitor_comparison_talktrack.html`); linked from reading guide (v3.1).

---

## Sales walkthrough (Phase C, 2026-06-16)

| Step | Artifact | Audience |
|------|----------|----------|
| 1 | `competitor_comparison_talktrack.html` 5 min | Любой stakeholder |
| 2 | `competitor_comparison_brief.html` | Presales first call |
| 3 | Segment one-pager (bank / industry / cloud) | ИБ / CFO / cloud-first |
| 4 | `competitor_comparison.html` Part II | Тендер, закупка |
| 5 | Methodology v1.6 + sources § | CFO sign-off |

Engineering acceptance: `test_competitor_products.py` green; default build → 5 baseline HTML + talk track.
