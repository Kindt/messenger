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
- Headroom badge: «до **N** рег. пользов. на тех же мощностях, без изменения цены/мощностей».
- `N` = `max_registered_users` выбранного infra-профиля Korus.

## Профили ≠ якоря

`pilot` / `standard` / `enterprise` — **профили infra Korus**, не точки сравнения.
Не использовать labels S-10k / E-1M / KORUS_ANCHORS.

## Обновление данных

1. Re-verify `source_url` на сайте вендора.
2. Bump `source_accessed_at`.
3. Запись в `CHANGELOG.md`.
4. Rebuild: `python scripts/presentation/build.py`.
