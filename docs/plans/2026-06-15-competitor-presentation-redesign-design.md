# Переработка презентации сравнения с конкурентами

**Дата:** 2026-06-15  
**Статус:** реализовано (v2.0 HTML, 2026-06-15)  
**Артефакты:** `competitor_comparison.html`, `scripts/competitor_comparison_data.py`, `docs/COMPETITOR_COMPARISON_METHODOLOGY.md`

---

## 1. Роль: аналитик

### 1.1 Текущее состояние

| Контур | Что есть | Охват |
|--------|----------|-------|
| **Production TCO** | Korus, eXpress, Пачка SaaS, VK SaaS | 4 решения, 5 якорей Korus |
| **Feature matrix** | + колонка Loop | 6 колонок, 10 строк |
| **Справочник** | Loop, Mattermost EE, Rocket.Chat, VK Superapp on-prem | без TCO, без плюсов/минусов |
| **Legacy §5.3** | 7 платформ + infra XMPP vs Korus | вне production-матрицы |
| **НТ §2.1** | QEMU baseline | только Korus |
| **Плюсы/минусы** | Korus, eXpress, Пачка, VK SaaS | 4 карточки |

**Сильные стороны:** единые ставки infra, чёткие floor Standard/Enterprise, eXpress sizing @100–1000, legacy для миграций, v1.5 единицы измерения.

**Пробелы (информативность):**

1. **Разрыв «справочник ↔ матрица»** — Loop/Rocket.Chat/Mattermost/VK on-prem упомянуты, но не сравниваются по TCO и compliance на якорях.
2. **Enterprise** — TCO-график только Korus+eXpress; SaaS-конкуренты отсутствуют на E-500k/E-1M (логично для on-prem, но не объяснено в UI).
3. **Нет «когда кого выбирать»** — заказчик не видит сценариев (гос/банк on-prem vs облако vs миграция с Jabber).
4. **Оси сравнения неполные** — нет строк: SLA, AD/LDAP/Keycloak, ретенция dual-TTL, MLS/E2EE статус, API/боты, air-gap, реестр ПО, стоимость внедрения.
5. **Плюсы/минусы асимметричны** — у Korus 5+/3−, у Пачки 3+/3−, нет карточек Loop, Rocket.Chat, VK on-prem, Mattermost, «DIY XMPP».
6. **Нет российских игроков вне списка** — потенциально: **Dialog (МТС)**, **Compass/Сбер**, **TrueConf** (UC+чат), **Яндекс 360** (workspace, не чистый IM) — требуют явного решения «в scope / out of scope».
7. **Навигация** — линейный HTML ~300 строк; нет executive summary, heatmap, фильтра «только on-prem».

### 1.2 Целевая аудитория документа

| Персона | Вопрос | Сейчас закрыт? |
|---------|--------|----------------|
| **CIO / IT-директор** | TCO на 10k/100k, on-prem | Частично |
| **ИБ / compliance** | export, legal hold, E2EE, ФСТЭК | Слабо (1–2 строки) |
| **Закупки** | прозрачный прайс, доля лицензии | Хорошо для eXpress/ SaaS |
| **Архитектор** | sizing, стек, интеграции | Средне |
| **Миграция с Jabber** | legacy + TCO | Хорошо в §5.3 |
| **Пилот / POC** | Pilot vs Standard | Есть сноска |

### 1.3 Конкурентная карта (целевая)

**Tier A — production-матрица (якоря ≥10k):** Korus, eXpress, Пачка, VK WorkSpace SaaS.

**Tier B — on-prem альтернативы (feature + оценочный TCO или «по КП»):** Loop, VK Superapp on-prem, Rocket.Chat EE, Mattermost EE.

**Tier C — справочно / миграция:** Legacy XMPP-стек, Sametime, Lync/SfB, Cisco UC.

**Tier D — явно out of scope (если нет данных):** Telegram, WhatsApp Business, зарубежные Slack/Teams как эталон (только footnote «не реестр РФ»).

**Tier E — исследовать:** Dialog, Compass, TrueConf — добавить после сбора публичных прайсов/sizing или пометить «нет публичных данных».

---

## 2. Роль: постановщик задачи

### 2.1 Цель

Переработать `competitor_comparison.html` в **самодостаточный аналитический документ** для переговоров и КП: максимальный охват релевантных конкурентов, симметричные плюсы/минусы, понятная методика сравнения.

### 2.2 Функциональные требования (FR)

| ID | Требование | Приоритет |
|----|------------|-----------|
| FR-1 | Executive summary (1 экран): кто для какого сценария | P0 |
| FR-2 | Расширить feature matrix до **≥18 критериев** и **≥8 колонок** (Tier A+B) | P0 |
| FR-3 | Карточки плюсов/минусов для **всех Tier A+B** (мин. 4+/4− каждая) | P0 |
| FR-4 | TCO: сохранить якоря Korus; добавить **пояснение «почему нет SaaS на Enterprise»** | P1 |
| FR-5 | Секция **«Оценочный TCO»** для Loop/Rocket.Chat (диапазон или «лицензия КП + infra модель») | P1 |
| FR-6 | **Heatmap** (SVG/CSS): критерий × продукт, цвет сила/слабость | P1 |
| FR-7 | **Decision tree** (текст + mermaid в HTML): контур → масштаб → рекомендация | P1 |
| FR-8 | Сравнение **моделей развёртывания** (on-prem / SaaS / hybrid BYOK) | P1 |
| FR-9 | Обновить `COMPETITOR_COMPARISON_METHODOLOGY.md` v1.4 синхронно | P0 |
| FR-10 | Версионирование презентации + дата ставок + changelog блок | P2 |

### 2.3 Нефункциональные (NFR)

- Генерация одной командой: `python scripts/build-competitor-comparison-html.py`
- Данные конкурентов — структуры в `competitor_comparison_data.py`, не размазаны по HTML
- Публичные источники с датой; оценки помечены «модель», не оферта
- Печать/PDF: `break-inside: avoid` для figure (уже есть)
- Русские подписи единиц (v1.5)

### 2.4 Критерии приёмки

1. В HTML **≥8 продуктов** с карточками плюсов/минусов.
2. Feature matrix **≥18 строк**, каждая ячейка — не пустой «—» без footnote где возможно.
3. Executive summary отвечает на 3 вопроса: on-prem vs SaaS, 10k vs 100k, миграция с legacy.
4. Методика и HTML согласованы по списку конкурентов и tier.
5. `py_compile` + ручной просмотр: нет `RU`/`user/мес` без расшифровки.

### 2.5 Out of scope (v2.0)

- Интерактивный фильтр JS (можно v2.1)
- Автопарсинг прайсов с сайтов конкурентов
- Персонализация под конкретного заказчика

---

## 3. Роль: архитектор

### 3.1 Информационная архитектура (новая структура HTML)

```
0. Executive summary + decision tree
1. Методика и профили (как сейчас §1)
2. Korus infra + НТ (§2)
3. TCO Tier A (графики + таблица)
4. TCO Tier B (оценочно, отдельный блок «альтернативы on-prem»)
5. Feature heatmap + полная матрица
6. eXpress deep dive (как §4)
7. Справочник Tier B sizing
8. Legacy и миграция (§5.3)
9. Плюсы/минусы (все продукты, группировка по tier)
10. Источники и дисклеймеры
```

### 3.1.1 Три подхода к расширению данных

| Подход | Плюсы | Минусы | Рекомендация |
|--------|-------|--------|--------------|
| **A. Монолит `competitor_comparison_data.py`** | Минимальный diff, уже работает | Файл >2000 строк | **Фаза 1** |
| **B. Split: `competitors/*.yaml` + loader** | Редактируют аналитики без Python | Новый парсер, CI | **Фаза 2** |
| **C. JSON Schema + validate в build** | Контракт, тестируемость | Overhead | Фаза 3 при росте |

**Рекомендация:** A для v2.0, заложить dataclass `ProductProfile` с tier, deployment, pros, cons, features.

### 3.2 Модель данных (целевая)

```python
@dataclass(frozen=True)
class ProductProfile:
    id: str                    # "korus", "express", "pachka-corp"
    name: str
    tier: Literal["A", "B", "C"]
    deployment: tuple[str, ...]  # "on-prem", "saas", "hybrid"
    license_model: str
    public_pricing: bool
    features: dict[str, str]   # criterion_id -> cell value
    pros: tuple[str, ...]
    cons: tuple[str, ...]
    tco_at_anchor: dict[str, CompetitorRow | None]  # anchor code
    sources: tuple[str, ...]
```

Критерии — enum/константа `CRITERIA: tuple[Criterion, ...]` с группами:

- **Compliance:** export, legal hold, dual-TTL, audit, ФСТЭК
- **Security:** E2EE/MLS, DLP, air-gap, SSO/Keycloak
- **Функции:** поиск, ВКС, mobile, bots, federation
- **Экономика:** прозрачность прайса, прозрачность sizing, TCO infra, TCO license
- **Эксплуатация:** SLA, HA ref-arch, ops stack complexity

### 3.3 Визуализации (новые)

| Визуал | Назначение |
|--------|------------|
| **Heatmap SVG** | 18×8 ячеек, цвет ✓/◐/—/✗ |
| **Radar (опционально)** | 6 осей для top-4 on-prem на одном слайде |
| **Stacked TCO** | как сейчас, + легенда tier |
| **Deployment badges** | иконки on-prem/SaaS в шапке таблиц |

### 3.4 Синхронизация с методикой

`COMPETITOR_COMPARISON_METHODOLOGY.md` → разделы:

- §6 список tier A/B/C
- §8 расширенная матрица (ссылка на criterion IDs)
- §9 оси плюсов/минусов — mapping на карточки

---

## 4. Роль: исполнитель (план реализации)

### Фаза 0 — подготовка данных (1–2 дня)

- [ ] T0.1 Инвентаризация публичных источников: Loop, Rocket.Chat, Mattermost, VK Superapp, Dialog, Compass
- [ ] T0.2 Заполнить `ProductProfile` для Tier A (рефактор из текущих dict)
- [ ] T0.3 Добавить Tier B с пометкой «оценка» где нет прайса

### Фаза 1 — контент (2–3 дня)

- [ ] T1.1 Расширить `CRITERIA` до 18+ строк
- [ ] T1.2 Карточки pros/cons для 8 продуктов (симметрия 4+/4−)
- [ ] T1.3 Executive summary + decision tree (HTML + mermaid через `<pre class="mermaid">` или SVG)
- [ ] T1.4 Пояснение Enterprise / SaaS gap

### Фаза 2 — визуал и сборка (1–2 дня)

- [ ] T2.1 `render_feature_heatmap_svg()`
- [ ] T2.2 Перестроить `build-competitor-comparison-html.py` по новой IA
- [ ] T2.3 Версия 2.0, CHANGELOG, methodology v1.4

### Фаза 3 — опционально

- [ ] T3.1 YAML split
- [ ] T3.2 Unit-тесты: все product id имеют pros/cons/features

---

## 5. Роль: тестировщик

### 5.1 Чеклист содержания

- [ ] Каждый Tier A продукт присутствует в: TCO @10k, feature matrix, pros/cons, pricing table
- [ ] Tier B — в feature matrix + pros/cons + справочник sizing; TCO помечен «оценка/КП»
- [ ] Legacy не смешивается с production TCO без баннера
- [ ] Pilot не в TCO-графиках
- [ ] Concurrent vs рег. пользов. — disclaimer на Mattermost/Rocket.Chat
- [ ] VK Superapp vs VK SaaS — явное «workspace vs только чат»

### 5.2 Чеклист технический

- [ ] `python scripts/build-competitor-comparison-html.py` exit 0
- [ ] Grep HTML: нет `@10k RU`, `100RU`, rps как ₽
- [ ] Все `<figure>` имеют `<figcaption>`
- [ ] Источники: каждый новый продукт — URL + дата

### 5.3 Чекlist сценариев (UAT)

| Сценарий | Ожидание |
|----------|----------|
| Заказчик «нужен on-prem 10k, compliance» | Summary → Korus / eXpress; heatmap export/legal hold |
| Заказчик «облако, быстро» | Summary → Пачка / VK SaaS; cons on-prem |
| Заказчик «с Jabber 2010» | §Legacy + migration + infra compare |
| Закупки «сравнить лицензию» | §3 license share + pricing table |
| Архитектор «сколько RAM» | §2 Korus + §4 eXpress RAM + Loop ref |

---

## 6. Роль: конечный пользователь (CIO / закупки)

### 6.1 Что должно быть «с первого экрана»

1. **Три рекомендации-сценария** (не «Korus лучше всех», а «если X — смотрите Y»).
2. **Таблица «кто участвует в TCO»** — Tier A full, Tier B partial, Tier C migration only.
3. **Дата и версия** — доверие к цифрам.

### 6.2 Боли текущей версии (user feedback симуляция)

- «Почему Loop в таблице функций, но нет в TCO?» → FR-5, пояснение tier.
- «Пачка дешевле по строке, но on-prem нельзя» → deployment dimension.
- «eXpress дорого — чем Korus выигрывает кроме цены?» → compliance + infra tier + NT.
- «У нас Sametime» → legacy уже хорош, нужна ссылка из summary.

### 6.3 UX-улучшения без JS

- Collapsible через `<details><summary>` для длинных таблиц eXpress
- Цветовые badge: `tier-a`, `tier-b`, `estimate`
- Оглавление с якорями на heatmap и decision tree

---

## 7. Конкуренты для добавления (черновик pros/cons)

### Loop (Tier B)

**Плюсы:** on-prem, реестр РФ, Mattermost-совместимость, низкий порог входа.  
**Минусы:** нет публичного sizing @10k+, compliance export — плагины, ВКС — интеграции, лицензия EE по КП.

### Rocket.Chat (Tier B)

**Плюсы:** open core, federation, mobile, marketplace.  
**Минусы:** MongoDB ops, EE pricing opaque, compliance не «из коробки» для РФ-аудита.

### Mattermost EE (Tier B)

**Плюсы:** ref-arch 15k/100k concurrent, зрелый продукт.  
**Минусы:** concurrent ≠ RU, не российский вендор, ФСТЭК — отдельный контур.

### VK Superapp on-prem (Tier B)

**Плюсы:** документирован sizing @2k, экосистема VK.  
**Минусы:** не «только мессенджер», @10k+ инд. проект, TCO сопоставим с workspace bundle.

---

## 8. Открытый вопрос для согласования

**Приоритет расширения:** что важнее в v2.0?

- **Вариант 1 (ширина):** Tier B on-prem (Loop, Rocket.Chat, Mattermost, VK Superapp) + heatmap + pros/cons  
- **Вариант 2 (глубина):** Tier A только, но +18 критериев, decision tree, compliance-фокус  
- **Вариант 3 (рынок РФ):** + Dialog/Compass/TrueConf после ресёрча, даже с «нет данных»

**Рекомендация архитектора:** Вариант 1 + executive summary (максимум полезности для переговоров on-prem vs SaaS).

---

## 9. Следующий шаг

После выбора приоритета (§8) — `speckit`-style tasks или прямой implement Фаза 0–1 в `competitor_comparison_data.py`.
