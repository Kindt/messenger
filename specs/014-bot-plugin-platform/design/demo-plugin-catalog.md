# Demo Plugin Catalog — Spec 014

**Date:** 2026-06-15  
**Status:** `draft` — brainstorming approved  
**Parent:** [`bot-plugin-platform-design.md`](bot-plugin-platform-design.md)

---

## 1. Цель каталога

Три аудитории — **один репозиторий**, разные «маршруты»:

| Маршрут | Аудитория | Что показываем |
|---------|-----------|----------------|
| **Vitrine** | Presales, заказчик на демо | 45-мин сценарий: L0→L2, Bitrix, SD, OCR teaser |
| **Developer** | Команда заказчика / интегратор | Echo на своём языке + SDK + sidecar за 1 день |
| **Integration lab** | Внутренняя разработка + CI | Каждая система: mock + smoke; опционально live sandbox |

**Принцип полноты:** каждая ось покрыта хотя бы одним runnable demo:

- классы **L0, L1, L2, L3** + **OCR**;
- runtime **C, B, D**;
- **polyglot** modern + legacy;
- интеграции из ТЗ: **Bitrix24, Exchange, диски, 1С, Jira, Confluence, Naumen**.

---

## 2. Структура каталога в репозитории

```
integrations/
├── demos/                          # runnable demos
│   ├── _mock-servers/              # shared mocks (WireMock / JSON fixtures)
│   ├── echo/                       # polyglot echo (корзина A)
│   ├── class-showcase/             # L0–L3 без внешней системы (корзина B)
│   ├── bitrix24/
│   ├── jira-confluence/
│   ├── exchange/
│   ├── storage/
│   ├── 1c/
│   ├── naumen/
│   ├── ocr/
│   └── ai/
├── bridges/                        # hot-plug workers (B) — prod code, demo config
├── sdk/                            # php, python, go, java, csharp, typescript
└── vitrine/                        # sales bundle
    ├── VITRINE_SCRIPT.md           # 45-min demo script (RU)
    ├── docker-compose.vitrine.yml  # mocks + selected demos
    └── manifest.yaml               # which bots register for vitrine profile
```

---

## 3. Корзина A — Polyglot Echo (один сценарий, N языков)

**Сценарий `echo-menu-v1` (идентичное поведение везде):**

1. @бот `ping` → `pong` + версия runtime  
2. Кнопка **«О проекте»** → текст + ссылка  
3. Кнопка **«Случайный совет»** → строка из локального JSON  
4. Slash `/echo <text>` → эхо (power user)

| ID | Путь | Язык / стек | Runtime | Legacy narrative | Smoke |
|----|------|-------------|---------|------------------|-------|
| A01 | `echo/echo-go` | Go 1.22 | sidecar D | modern minimal | P0 |
| A02 | `echo/echo-python` | Python 3.12 | sidecar D | AI/OCR команда | P0 |
| A03 | `echo/echo-php82` | PHP 8.2 | sidecar D | Bitrix-ecosystem modern | P0 |
| A04 | `echo/echo-php74` | PHP 7.4 procedural | sidecar D | shared hosting | P1 |
| A05 | `echo/echo-java8` | Java 8 | sidecar D | «не трогаем монолит» | P1 |
| A06 | `echo/echo-vbnet48` | VB.NET Framework 4.8 | sidecar D | Win-сервер | P1 |
| A07 | `echo/echo-powershell51` | PowerShell 5.1 | sidecar D | ops one-off | P1 |
| A08 | `echo/echo-typescript` | Node 20 + TS | sidecar D | frontend команда | P2 |
| A09 | `echo/echo-csharp8` | C# .NET 6 | sidecar D | modern .NET | P2 |
| A10 | `echo/echo-java21` | Java 21 | sidecar D | same as core stack | P2 |
| A11 | `echo/echo-delphi` | Free Pascal / Delphi | sidecar D | промышленность | P3 optional |
| A12 | `echo/echo-ruby` | Ruby 3.2 | sidecar D | legacy web | P3 optional |

**CI P0:** A01 + A02 + A03. **Vitrine quick:** показать 2 стека подряд (PHP + PowerShell) — «один контракт».

---

## 4. Корзина B — Class showcase (лестница L0–L3)

| ID | Имя витрины | Класс | Runtime | Язык | Сценарий для пользователя |
|----|-------------|-------|---------|------|---------------------------|
| B01 | `hr-faq-bot` | **L0** | config-only (C) | — | Меню: отпуск, пропуск, ИТ — тексты + ссылки |
| B02 | `phone-directory` | **L0** | config-only | — | Кнопки отделов → extension + email |
| B03 | `wiki-search-demo` | **L1** | connector C | YAML mapping | @бот «регламент командировки» → mock Confluence hit |
| B04 | `ticket-status-demo` | **L1** | connector C | YAML | Кнопка «Мой тикет» → mock Jira status |
| B05 | `create-ticket-demo` | **L2** | connector C + mock | — | Slash `/ticket` → карточка + кнопка «Создать» → mock Jira |
| B06 | `channel-digest-demo` | **L2** | connector C | — | Cron → digest в канал (mock events) |
| B07 | `sd-full-demo` | **L2** | naumen-bridge B | Java | Заявка из чата + push смены статуса (mock Naumen) |
| B08 | `ocr-invoice-demo` | **OCR→L2** | ocr-worker B | Python | PDF в чат → поля → «Отправить в учёт» (mock 1C) |
| B09 | `ai-triage-demo` | **L3** | ai-bridge B | Python | Тред → классификация + draft ticket (on-prem LLM mock) |

**Vitrine 45 min:** B01 → B04 → B05 → B08 (teaser B09 если LLM поднят).

---

## 5. Корзина C — Integration reference (по системам)

**Правило:** каждая система = **mock (CI)** + **live profile (опционально)** в `manifest.yaml`.

| ID | Система | Класс | Реализация | Язык (reference) | Дубль на другом языке | Mock |
|----|---------|-------|------------|------------------|----------------------|------|
| C01 | **Bitrix24** | L1/L2 | sidecar D | **PHP 8.2** | PHP 7.4 (C01b) | `mock-bitrix24` |
| C02 | **Bitrix24** tasks | L2 | sidecar D | PHP 8.2 | — | same mock |
| C03 | **Jira** | L1/L2 | connector C | YAML + Go adapter | TS sidecar (C03b) | `mock-jira` |
| C04 | **Confluence** | L1/L2 | connector C | YAML | Python sidecar (C04b) | `mock-confluence` |
| C05 | **MS Exchange** | L1/L2 | exchange-bridge B | **C#** | — | `mock-graph` |
| C06 | **Диск** WebDAV/SMB | L1/L2 | storage-bridge B | **Python** | Go (C06b) | mock FS in container |
| C07 | **1С ERP** status | L1 | 1c-bridge B | **C#** | 1C HTTP-сервис sample | `mock-1c-odata` |
| C08 | **1С** согласование | L2 | 1c-bridge B | C# | — | mock |
| C09 | **Naumen** SD | L1/L2 | naumen-bridge B | **Java** | Python (C09b) | `mock-naumen` |
| C10 | **Custom ERP** | L2 | sidecar D | **любой** | echo-style duplicates | generic mock REST |

**Bitrix24 (обязательный flagship):**

- C01: `@crm найти контакт Иванов` → карточка из mock CRM  
- C02: кнопка «Создать лид из сообщения» + webhook стадии → сообщение в канал `#sales`  
- C01b: тот же сценарий на PHP 7.4 — slide «ваш хостинг без апгрейда»

**Дублирование языков (не всё на всём):**

| Интеграция | Primary | Secondary (витрина «два стека») |
|------------|---------|----------------------------------|
| Bitrix24 | PHP 8.2 | PHP 7.4 |
| Jira | connector YAML | Go sidecar |
| 1С | C# bridge | mock only for 1C HTTP |
| Naumen | Java bridge | Python sidecar |
| Exchange | C# | — |
| Storage | Python | Go |

---

## 6. Vitrine bundle (presales)

**Профиль `vitrine-full`** (`integrations/vitrine/manifest.yaml`):

```yaml
profile: vitrine-full
duration_min: 45
demos:
  - B01-hr-faq-bot          # 5 min — L0 без кода
  - A03-echo-php82          # 3 min — polyglot
  - A07-echo-powershell51   # 3 min — legacy ops
  - C01-bitrix24-crm        # 10 min — PHP flagship
  - C03-jira-create         # 8 min — L2
  - C09-naumen-ticket       # 8 min — SD + push
  - B08-ocr-invoice         # 8 min — OCR on-prem
optional:
  - B09-ai-triage           # если on-prem LLM mock up
```

**Профиль `vitrine-short` (15 min):** B01 → C01 → C03.

**Профиль `developer-day`:** A01–A03 + SDK walkthrough + register own sidecar.

---

## 7. Mock vs live (decision **C** — hybrid)

| Режим | Назначение | Когда |
|-------|------------|-------|
| **mock** | CI, QEMU smoke, офлайн dev | **default** — все smokes |
| **vitrine-light** | Presales без внешних систем | `docker-compose.vitrine.yml` — mocks only |
| **vitrine-heavy** | Presales «как в жизни» | `docker-compose.vitrine-heavy.yml` — коробочные песочницы |

### mock (default)

- `_mock-servers/`: WireMock / JSON fixtures для Jira, Confluence, Naumen, 1C OData, Graph API, Bitrix REST
- Детерминированные smokes; не требует лицензий

### vitrine-light

- Тот же compose что CI + sidecars/bridges из `manifest.yaml` profile `vitrine-full`
- Подходит: внутреннее демо, заказчик без доступа к своим системам

### vitrine-heavy

**Отдельный compose** для presales с **реальными или коробочными** компонентами:

| Сервис | Образ / источник | Зачем на витрине |
|--------|------------------|------------------|
| **Bitrix24** | Коробка / Bitrix Environment (лицензия dev) | Flagship PHP demo на живом REST |
| **Jira** | Atlassian DC trial / `atlassian/jira-software` | Create issue + webhook |
| **Confluence** | paired with Jira or mock fallback | Wiki search live |
| Exchange | optional — часто mock Graph достаточно | календарь — по запросу |
| 1С | mock OData default; live — только если у заказчика VPN | не в heavy по умолчанию |
| Naumen | mock; live — staging заказчика | |

**Правила vitrine-heavy:**

- Не входит в CI — manual / nightly optional job
- Документ `vitrine/HEAVY_PREREQUISITES.md`: RAM, лицензии, порты
- Env-переключатель: `VITRINE_PROFILE=light|heavy`
- Fallback: если Bitrix box не поднялся → auto-fallback на mock (скрипт preflight)

```yaml
# integrations/vitrine/manifest.yaml (фрагмент)
profiles:
  vitrine-full:
    backend: mock          # default for CI
  vitrine-full-heavy:
    extends: vitrine-full
    backend: heavy         # docker-compose.vitrine-heavy.yml
    requires:
      - bitrix24-dev
      - jira-dev
```

---

## 8. Smokes (минимальный набор)

| Smoke | Covers |
|-------|--------|
| `smoke-plugin-echo-php.ps1` | A03 |
| `smoke-plugin-echo-go.ps1` | A01 |
| `smoke-plugin-l0-faq.ps1` | B01 |
| `smoke-plugin-bitrix24-mock.ps1` | C01 |
| `smoke-plugin-jira-mock.ps1` | C03 L2 |
| `smoke-plugin-outbound-mock.ps1` | B06/C09 push |
| `smoke-plugin-ocr-mock.ps1` | B08 |
| `smoke-vitrine-bundle.ps1` | manifest vitrine-full |

---

## 9. Phasing (реализация каталога)

| Phase | Demos | Count |
|-------|-------|-------|
| **P0** | A01–A03, B01, C01 mock, mocks infra | ~8 |
| **P1** | A04–A07, B03–B05, C03–C04, C09, vitrine-short | +12 |
| **P2** | C05–C08, B06–B08, A08–A10, vitrine-full | +15 |
| **P3** | B09, A11–A12, live profiles, C03b/C09b | +8 |

**Итого каталог:** ~43 demo artifacts (не все — отдельные Docker; echo дубли легковесные).

---

## 10. Документация для заказчика

| Doc | Содержание |
|-----|------------|
| `integrations/README.md` | индекс каталога |
| `integrations/vitrine/VITRINE_SCRIPT.md` | сценарий демо RU |
| `integrations/LEGACY_PATH.md` | «как задействовать PHP/Java8/VB команду» |
| Per-demo `README.md` | env, register bot, 3 команды запуска |

---

*Approved composition: vitrine + integrations + polyglot — full catalog.*
