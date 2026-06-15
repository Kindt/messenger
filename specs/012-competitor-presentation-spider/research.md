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
| — | — | A | pending |
| — | — | B | pending |
| — | — | C | pending |

---

## Open questions

1. **TrueConf battle card** — P0 optional or defer to P1 if HTML length concern?
2. **ФСТЭК wording** — «в процессе» vs target certification date (product input).
3. **Talk track** — separate HTML vs expandable brief section (prefer separate file if >2 screens).
