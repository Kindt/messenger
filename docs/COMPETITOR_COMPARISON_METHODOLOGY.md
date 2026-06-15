# Методика сравнения Korus Messenger с конкурентами

**Версия:** 1.5  
**Дата:** 2026-06-15  
**Назначение:** единые правила для презентаций, КП и переговоров. Документ **самодостаточен** — не ссылается на внутренние артефакты репозитория.

**Визуальная презентация:**

| Файл | Назначение | Сборка |
|------|------------|--------|
| [`competitor_comparison_brief.html`](../competitor_comparison_brief.html) | One-pager для встречи / PDF (~6–8 стр.) | `python scripts/build-competitor-comparison-html.py --brief-only` |
| [`competitor_comparison.html`](../competitor_comparison.html) | Полная версия (TCO, legacy, НТ, 11 продуктов) | `python scripts/build-competitor-comparison-html.py` (оба файла по умолчанию) |

---

## 1. Роли профилей Korus

| Профиль | Назначение | Участвует в сравнении с конкурентами |
|---------|------------|--------------------------------------|
| **Pilot (пробник)** | POC, оценка, филиал на время пилота | **Нет** — отдельная сноска |
| **Standard** | Промышленная эксплуатация | **Да** |
| **Enterprise** | Крупный и федеральный контур | **Да** |

---

## 2. Минимальные пороги (floor)

| Конфигурация | Минимум RU | Максимум RU | Если у конкурента меньше минимума |
|--------------|------------|-------------|-----------------------------------|
| **Standard** | **10 000** | **100 000** | Не в production-матрице; только пробник |
| **Enterprise** | **100 000** | **1 000 000** | Не Enterprise; максимум Standard на 100 000 |

**100 000 пользователей** — потолок Standard и нижняя граница Enterprise. Для сравнения на 100 000 используем **Standard на 100 000** (детальный расчёт infra). Enterprise на 100 000 — только при необходимости SLA 99,9% и RPO 15 минут.

**Не округлять Standard вниз.** eXpress на 5 000 пользователей не сравниваем со Standard Korus.

---

## 3. Фиксированные точки расчёta (якоря)

### Standard (production)

| Код | RU | Пик онлайн | Пик msg/s | RAM (цель) | Диск 1 год |
|-----|-----|------------|-----------|------------|------------|
| **S-10k** | 10 000 | ~750 | ~15 | ~64 GB | ~5–8 TB |
| **S-50k** | 50 000 | ~2 400 | ~90 | ~140 GB | ~110 TB |
| **S-100k** | 100 000 | ~4 800 | ~120 | ~140 GB | ~30 TB |

Standard на 10 000 — **полный production-функционал** (Solr, ретенция, export), не пробник.

### Enterprise (production)

| Код | RU | Пик онлайн | Пик msg/s | RAM (цель) | Диск 1 год |
|-----|-----|------------|-----------|------------|------------|
| **E-100k** | 100 000 | ~4 800 | ~120 | ~140 GB | ~30 TB |
| **E-500k** | 500 000 | ~15 000 | ~400 | ~450 GB | ~110 TB |
| **E-1M** | 1 000 000 | ~20 000 | ~600 | ~0,9–1,2 TB | ~200 TB |

---

## 4. Условные тарифы инфраструктуры (₽/мес, без НДС)

| Статья | Единица | ₽/мес |
|--------|---------|-------|
| Сервер приложений | 16 GB RAM, 8 vCPU | 28 000 |
| Сервер приложений | 32 GB RAM, 8 vCPU | 45 000 |
| Сервер full stack | 64 GB RAM, 16 vCPU | 72 000 |
| Web + балансировщик | 8 GB RAM, 4 vCPU | 15 000 |
| PostgreSQL primary + replica | 2 × 32 GB | 90 000 |
| Redis cluster | 3 узла | 25 000 |
| Solr + ZooKeeper | 3 × 16 GB | 75 000 |
| NATS, Keycloak, workers | набор JVM | 45 000 |
| Диск SSD | 1 TB | 3 500 |
| Диск HDD | 1 TB | 800 |
| Канал 200 Мбит/с | — | 8 000 |
| Канал 1 Гбит/с | — | 35 000 |
| Ops Pilot | контур | 5 000 |
| Ops Standard+ | контур | 15 000 |

**Дата ставок:** 2026-06-15, регион РФ (Москва/СПб), коммерческий сегмент. Учебный шаблон для КП, не оферта.

---

## 5. Infra Korus по якорям

| Якорь | Infra ₽/мес | Infra ₽/год | ₽/user/мес (только infra) |
|-------|-------------|-------------|---------------------------|
| Пробник (вне матрицы) | 61 350 | 736 200 | ~6,1 |
| **S-10k** | 109 550 | 1 314 600 | ~11 |
| **S-50k** | 220 775 | 2 649 300 | ~4,4 |
| **S-100k** | 332 000 | 3 984 000 | ~3,3 |
| **E-500k** | ~1 000 000* | ~12 000 000* | ~2,0* |
| **E-1M** | ~2 000 000* | ~24 000 000* | ~2,0* |

\* Оценка по sizing крупного кластера; уточняется на stage load test.

Лицензия Korus — отдельная строка КП (модель не обязательно per-user).

---

## 6. Сопоставление конкурентов

### 6.0 Tier-модель (презентация v2.2)

**Реестр данных:** `scripts/competitors/registry.json` — продукты, 18 критериев, pros/cons, radar, pricing constants. Загрузка: `scripts/competitor_registry_loader.py`; проверка: `python scripts/test_competitor_products.py` (входит в `./gradlew buildIntegrity`). Экспорт после правок Python: `python scripts/export_competitor_registry_json.py`.

| Tier | Решения | TCO @якорях | Feature matrix |
|------|---------|-------------|----------------|
| **A** | Korus, eXpress, Пачка, VK SaaS | полный (§7) | да |
| **B** | Loop, Rocket.Chat, Mattermost EE, VK Superapp on-prem | оценочный @10k | да |
| **C** | МТС Линк Чаты, Compass, TrueConf Server | прайс/КП | да |
| **Legacy** | XMPP, Sametime, Lync, … | infra-only справочно | §5.3 |

**Tier C (рынок РФ, 2026):**

| Решение | Публичный прайс | Примечание |
|---------|-----------------|------------|
| **МТС Линк Чаты** | по КП | on-prem + SaaS; реестр РФ; преемник Dialog |
| **Compass** | 390 ₽/мес облако; 490 ₽/мес on-prem | getcompass.ru/pricing |
| **TrueConf Server** | от 23 000 ₽/год | UC+чат; PRO/online/guest, не linear ₽/reg |

```
RU < 10 000   →  не production; пробник или «ниже порога»
10 000–100 000 → якорь Standard (10k | 50k | 100k)
RU > 100 000   → якорь Enterprise (100k | 500k | 1M)
```

### eXpress (приоритет)

- Лицензия Corporate on-prem: **3 000 ₽/user/год** (публичный прайс express.ms).
- SmartApps: **5 000 ₽/user/год**; Lite: **200 ₽/user/год**; продление SmartApps: **2 500 ₽/user/год**.
- Железо (публичные таблицы вендора):

| RU | vCPU | RAM | SSD | Источник |
|----|------|-----|-----|----------|
| 100 | 11 | 17 GB | 0,43 TB | docs.express.ms |
| 500 | 37 | 42 GB | 1,10 TB | обзор eXpress 3.48 |
| 1 000 | 62 | 62 GB | 2,18 TB | обзор eXpress 3.48 |
| >5 000 | — | — | — | индивидуальный проект |

- Роли @100 RU: Proxy, Media, Transcoding, Back CTS, Bot (детализация в HTML-презентации).
- Media: ~0,3 vCPU × участник; ~10% users в звонке одновременно.
- @10k / @100k infra — **модельная оценка** (упаковка RAM/SSD по тем же ставкам, что Korus); не оферта вендора.
- Внедрение не включено в лицензию.

### VK WorkSpace

- SaaS от **207 ₽/user/мес** (годовая оплата).
- On-prem sizing мессенджера — индивидуальный расчёт.

### Пачка

- Облако, тариф «Корпорация»: **399 ₽/user/мес** при оплате за год.
- Тариф «Компания»: **159 ₽/user/мес** при оплате за год.
- Железо заказчика: 0.

### Loop / Mattermost

- Open-source ref-arch; Loop 1–2k RU на одном сервере (2–4 GB RAM).
- Mattermost ref-arch использует **concurrent users** — не смешивать с RU без оговорки.

### Устаревшие платформы (legacy) — вне production-матрицы

Справочный контур для заказчиков с действующим Jabber/XMPP, Sametime, Lync/Skype for Business. **Не участвуют** в TCO-battle §7 наравне с eXpress.

| Платформа | Протокол | Типичная эпоха | Лицензия | Примечание |
|-----------|----------|----------------|----------|------------|
| **XMPP / Jabber** | XMPP | 2000–2010-е | OSS (ejabberd, Openfire, Prosody) | «Банковский Jabber»; federation |
| **ejabberd** | XMPP | 2000–2020-е | GPL + support | Кластер Erlang; MAM/roster |
| **Openfire** | XMPP | 2000–2010-е | Apache 2.0 | Java; single-node до ~5k |
| **Prosody** | XMPP | 2010-е | MIT | Лёгкий; малые контуры |
| **IBM / HCL Sametime** | проприетарный | 2000–2010-е | коммерческая | Domino/Notes интеграция |
| **Lync / Skype for Business** | SIP/MS | 2010–2020-е | EA | Путь миграции → Teams |
| **Cisco Jabber (UC)** | SIP/XMPP | 2010-е | Cisco | Часть CUCM/IMP, не только чат |

**Infra-only (XMPP HA, те же ставки):**

| RU | XMPP HA ₽/год | XMPP 1-node ₽/год | Korus ₽/год |
|----|----------------|-------------------|-------------|
| 2 000 | 660 000 | 660 000 | — (ниже floor) |
| 10 000 | 1 716 000 | 864 000 | 1 314 600 (S-10k) |
| 100 000 | 4 452 000 | 2 208 000 | 3 984 000 (S-100k) |

HA XMPP @10k **дороже** Korus infra (нет оптимизации monolith); @100k — сопоставимо. Single-node legacy дешевле, но без отказоустойчивости и compliance-стека.

Лицензия OSS = 0; **скрытые costs** — интеграция, поддержка, миграция MAM/roster, compliance DIY.

**Миграция на Korus:** экспорт истории (MAM), смена клиентов, пересмотр federation; выигрыш — export gate, dual-TTL, поиск, единый вендор.

---

## 6.1. Engineering baseline НТ (QEMU)

Замеры на виртуалке (2026-06-15) — **не production TCO**, но подтверждают работоспособность стека Korus:

| Метрика | Замер | Проект S-10k |
|---------|-------|--------------|
| Health p95 | 11 ms | &lt; 500 ms (k6 порог) |
| REST read rps | ~250 | — |
| E2E burst msg/s | ~6 | ~15 (пик Pilot) |

Источник: [`docs/benchmarks/qemu-nt-baseline-2026-06-15.json`](benchmarks/qemu-nt-baseline-2026-06-15.json), визуализация — [`competitor_comparison.html`](../competitor_comparison.html) §2.1.

Детали legacy-платформ — [`competitor_comparison.html`](../competitor_comparison.html), раздел 5.3.

---

## 7. Матрица слайдов (production)

| № | Якорь Korus | RU | Конкуренты |
|---|-------------|-----|------------|
| 1 | S-10k | 10 000 | eXpress, VK SaaS, Пачка |
| 2 | S-50k | 50 000 | eXpress (КП), VK SaaS, Пачка |
| 3 | S-100k | 100 000 | eXpress, VK, Пачка |
| 4 | E-500k | 500 000 | eXpress (КП) |
| 5 | E-1M | 1 000 000 | eXpress (КП) |

---

## 8. Матрица возможностей (кратко)

| Ось | Korus | eXpress | Пачка | VK SaaS | Loop/Mattermost |
|-----|-------|---------|-------|---------|-----------------|
| On-prem | ✓ | ✓ | — | — | ✓ |
| Export / legal hold | ✓ ядро | DLP/политики | export API | зависит | плагины |
| E2EE / MLS | ✓ (roadmap active) | ✓ | — | — | плагины |
| ВКС | ✓ (WebRTC) | ✓ Media | — | ✓ | плагины |
| Прозрачность sizing | высокая | @100–1k; >5k КП | N/A | N/A | ref-arch |

Полная таблица — в [`competitor_comparison.html`](../competitor_comparison.html), раздел 5.

---

## 9. Оси для плюсов/минусов

1. On-prem / изолированный контур  
2. Compliance (export, legal hold, ретенция, audit)  
3. TCO инфраструктуры  
4. TCO лицензий (прозрачность)  
5. Прозрачность sizing  
6. SLA / отказоустойчивость  
7. ВКС и медиа  
8. Мобильные клиенты / суперапп  

---

## 10. Дисклеймеры

- Цифры infra — ориентиры; formal load test до prod sign-off рекомендуется.
- Активные видеозвонки могут удвоить сеть и CPU.
- Лицензии конкурентов — из публичных прайсов на дату документа.
- Concurrent (Mattermost) и RU (Korus, eXpress) не смешивать без пересчёта.
