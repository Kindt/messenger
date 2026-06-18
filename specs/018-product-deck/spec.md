# Spec 018 — Product Deck (4-tab HTML, GitHub Pages)

## Goal

Единая самодостаточная landing-страница **`docs/index.html`** на GitHub Pages: честный Block 0 («рабочий прототип»), 4 persona-вкладки × 4 подраздела, сравнение с конкурентами по их публичным тарифам, 4 калькулятора, honesty gate.

## User stories

| ID | Story |
|----|-------|
| US1 | Заказчик видит Block 0 с blockers и матрицей функций до маркетинга |
| US2 | РП/аналитик — roadmap, риски, traceability без overclaim |
| US3 | DevOps/backend — стек, sizing, RAM/TCO @ RU конкурента |
| US4 | Presales — TCO и deployment cards с источниками цен |
| US5 | Офисный пользователь — wizard/FAQ без tech jargon |
| US6 | CI — `buildIntegrity` + honesty_check + offerings schema |

## Out of scope

- Production-ready claims, ФСТЭК/реестр overclaim
- Якоря S-10k/E-1M в UI и данных
- Live-server ops (spec 015)
- External CDN/fonts (offline deck)

## Contracts

- `contracts/deck-acceptance.json` — A0–A10 acceptance criteria

## Publication

- Branch `main`, folder `/docs`
- URL: `https://kindt.github.io/messenger/` (repo `Kindt/messenger`)
