# METRIC_POLICY — сравнение тарифов конкурентов (v1)

## Правила метрик

| Правило | Действие |
|---------|----------|
| `metric == registered_users` + `billing_unit == registered_users` + `tco_comparable: true` | TCO compare + headroom |
| `metric == active_users` | TCO compare **запрещён** v1: не пересчитываем активных пользователей в RU без явного коэффициента |
| `metric == concurrent_users` | TCO compare **запрещён** v1 |
| `metric == korus_infra_reference` | Только справочная строка Korus infra; не конкурентный тариф и не TCO-row |
| **`source_url` не HTTPS** | offering invalid (CI fail) |
| **`price_is_public: false`** | qualitative only; no competitor ₽ in table |
| Нет открытого источника | **не включать** offering в TCO; можно в feature matrix |

## Headroom

- Korus считается на **тех же RU**, что указаны у конкурента в TCO-comparable строке.
- Headroom badge: «до **N** рег. пользов. на тех же мощностях».
- `N` = max RU в том же VM-тир RAM (prod full, `module_sizing` / `#tech-s4`).
- Для `active_users`, `concurrent_users`, `quote` и `no_public_price` headroom не показывается.

## Sizing в deck

- **Prod full** — единственный контур для калькулятора и TCO (`full-server.yml`, `--profile full`).
- **Ядро** (locked): postgres-hot, redis, nats, minio, keycloak, core-api, ws-gateway, web-lb, worker-message-pipeline.
- **Опции baseline** (галка, по умолчанию вкл.): postgres-archive, solr, zookeeper, workers retention/export/deep-archiver/archiver/indexer/push/preview.
- **Опции по запросу** (галка, по умолчанию выкл.): livekit, worker-bot-delivery, integrations (L1–L3).
- **Зависимости**: zookeeper→solr; worker-indexer→solr; worker-archiver→postgres-archive.
- **Dev-min** — только QEMU/разработка; **не** использовать в product deck sizing.
- **Base + add-ons** — единственная модель продукта в deck; никаких product-tier labels.

## Sizing gate (spec 021 Phase 8.2)

Единая методика для deck `#tech-s4` и `module_sizing.py`:

| Правило | Источник |
|---------|----------|
| **Prod full** — единственный контур калькулятора | `docker-compose.full-server.yml` + `--profile full` |
| **Base locked** — ядро без галочек | `docs/product-modules.yaml` → `base.core_infra` |
| **Add-ons** — галочки = `korus_product_addons` | Каталог `addons[]`; sizing union через `scripts/presentation/module_sizing.py` |
| **`addon-search`** | Lab: SQL fallback (`lean-stack-up` без Solr); prod: Solr+ZK+indexer — см. [`DEV_STACK_PROFILES.md`](../docs/DEV_STACK_PROFILES.md) §5 |
| **Concurrent users** | Не сравнивать с конкурентами в TCO v1 (см. выше) |
| **Formal load @ stage** | k6 baseline — spec **015** LSO-004 (Sep 2026+) |

Rebuild deck после изменения каталога или политики: `python scripts/presentation/build.py`.

## Обновление данных

1. Re-verify `source_url` на сайте вендора.
2. Проверить `metric`, `billing_unit`, `tco_comparable`:
   - цена за зарегистрированного пользователя → `registered_users` / `registered_users` / `true`;
   - цена за активного пользователя → `active_users` / `active_users` / `false`;
   - КП или нет публичной цены → `billing_unit: quote`, `tco_comparable: false`;
   - справочная строка Korus infra → `korus_infra_reference` / `infra_reference` / `false`.
3. Bump `source_accessed_at`.
4. Запись в `CHANGELOG.md`.
5. Rebuild: `python scripts/presentation/build.py`.
