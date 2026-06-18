# METRIC_POLICY — сравнение тарифов конкурентов (v1)

## Правила метрик

| Правило | Действие |
|---------|----------|
| `metric == registered_users` | TCO compare + headroom **только if** `price_is_public` |
| `metric == concurrent_users` | TCO compare **запрещён** v1 |
| **`source_url` не HTTPS** | offering invalid (CI fail) |
| **`price_is_public: false`** | qualitative only; no competitor ₽ in table |
| Нет открытого источника | **не включать** offering в TCO; можно в feature matrix |

## Headroom

- Korus считается на **тех же RU**, что указаны у конкурента в строке.
- Headroom badge: «до **N** рег. пользов. на тех же мощностях».
- `N` = max RU в том же VM-тир RAM (prod full, `module_sizing` / §10.3).

## Sizing в deck

- **Prod full** — единственный контур для калькулятора и TCO (все модули `full-server.yml`).
- **Dev-min** — только QEMU/разработка; **не** использовать в product deck sizing.
- **Pilot / Standard / Enterprise** — **не** использовать в deck; устаревшие product-tier labels.

## Обновление данных

1. Re-verify `source_url` на сайте вендора.
2. Bump `source_accessed_at`.
3. Запись в `CHANGELOG.md`.
4. Rebuild: `python scripts/presentation/build.py`.
