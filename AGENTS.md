<!-- SPECKIT START -->
Current plan: `specs/011-korus-cloud-platform/plan.md`
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

# AGENTS.md — база знаний для AI-агентов и разработчиков

Постоянно развивающийся справочник по проекту **Korus Messenger (AvandocMsg)**.  
После каждой значимой задачи проверяй необходимость обновления этого файла и связанных документов (см. раздел «Сопровождение документации»).

---

## 1. Назначение проекта

**Korus Messenger** — корпоративный мессенджер с сервером на Java, фоновыми воркерами, веб-клиентом и развёртыванием через Docker/Ansible.

Основные возможности:

- чаты, сообщения, файлы, конференции, push, E2EE/MLS;
- ретенция и deep-archive, экспорт для комплаенса;
- WebSocket через ws-gateway, поиск (Solr), object storage (MinIO);
- админ-консоль, Keycloak/JWT, мультитенантность (организации).

Целевая аудитория репозитория: backend, frontend webui, DevOps, QA и агенты автоматизации (Cursor, CI).

---

## 2. Технологии и архитектурные принципы

### Стек

| Слой | Технологии |
|------|------------|
| Runtime | **Java 25**, embedded **Tomcat 11**, **Jersey (JAX-RS) 4.0** — не Spring Boot |
| Сборка | **Gradle** (Kotlin DSL), `./gradlew buildIntegrity` |
| БД | **PostgreSQL 16**, **Flyway**, **H2** в тестах |
| Messaging | **NATS 2.10** (+ JetStream), контракты в `modules/common` |
| Cache / search / storage | **Redis**, **Solr**, **MinIO** |
| Auth | **Keycloak 24**, JWT (Nimbus) |
| Web UI | Vanilla JS **`modules/web-client/src/main/resources/webui/`**, прокси `/api/*` |
| Deploy | **Docker Compose**, **Ansible** |
| E2E | **Playwright** (`tests/e2e-web/`), smoke-скрипты (`scripts/`) |
| Метрики | Prometheus **simpleclient** |

### Принципы (конституция)

Источник истины: [`.specify/memory/constitution.md`](.specify/memory/constitution.md) (v1.1.0).

Кратко:

1. **Spec-first, contract-driven** — OpenAPI/NATS до кода; `@JsonAlias` для совместимости.
2. **Retention & compliance by design** — dual-TTL, audit, export/deep-archive.
3. **Testability** — JUnit 5, H2 для репозиториев, тесты воркеров.
4. **Observability** — метрики Prometheus, `/health`/`/ready`, таймауты SQL через env.
5. **Modular monolith** — `workers/*` → `core-api` → `common`; логика в сервисах, не в `*Resource`.
6. **Infrastructure parity** — prod-like стек для smoke; env-конфигурация, без секретов в коде.
7. **Hexagonal (в процессе)** — `core.domain` / `core.application` / `core.port` / `core.adapter.*` — см. [`docs/ARCHITECTURE_CORE_PACKAGES.md`](docs/ARCHITECTURE_CORE_PACKAGES.md).

Исключение **bounded deployment split** (hot-plug workers): только с ADR и graceful degradation — [`docs/adr/ADR-hotplug-deployment-split.md`](docs/adr/ADR-hotplug-deployment-split.md).

---

## 3. Структура проекта

```
korus_messenger/
├── modules/
│   ├── common/           # NATS DTO, shared types, hotplug, admin SPI
│   ├── core-api/         # REST API, Flyway, domain/application/ports (hex)
│   ├── web-client/       # Tomcat + webui (статика, прокси API)
│   ├── ws-gateway/       # WebSocket gateway
│   └── workers/          # message-pipeline, retention, export-replay, push, …
├── services/indexer/     # выделенный hot-plug indexer (ADR)
├── docker/               # Dockerfile*, compose overlays
├── deploy/
│   ├── ansible/          # roles, playbooks (Linux, CI)
│   └── two-host/         # LAN: server host + web host
├── .cursor/
│   ├── skills/           # speckit-* (committed), superpowers-* (junctions), korus-agent-workflow
│   ├── superpowers/      # vendor clone cursorpowers (gitignored)
│   └── install-superpowers.ps1
├── korus-web/            # Docker: nginx + replicas web-client
├── scripts/              # smoke, stack-up, presentation, profiling
├── tests/e2e-web/        # Playwright specs, playwright-tiers.json
├── specs/                # Spec-kit: spec/plan/tasks/contracts per feature
├── docs/                 # architecture, ADR, plans, review, roadmap
└── .specify/             # constitution, templates (speckit)
```

### Назначение ключевых каталогов

| Каталог | Назначение |
|---------|------------|
| `modules/core-api/.../api/` | JAX-RS resources (переходный слой; новый код — в hex-пакеты) |
| `modules/core-api/.../resources/db/migration/` | Flyway — **один владелец миграций на спринт** |
| `modules/web-client/.../webui/` | UI: `app.js`, locales, E2EE WASM hooks |
| `specs/00N-*` | feature specs (001 review, 002 parity, 003 deploy, 004 closure) |
| `.cursor/rules/` | правила агента (speckit plan, ops-live-server-deferred, …) |

### Gradle-модули

См. [`settings.gradle.kts`](settings.gradle.kts): `core-api`, `web-client`, `ws-gateway`, `common`, workers (`retention`, `message-pipeline`, `export-replay`, …), `services:indexer`.

---

## 4. Правила разработки (характерные для проекта)

### Общие

- **Минимальный diff** — только запрошенное; не рефакторить «заодно».
- **Конвенции окружения** — читай соседний код перед правками; match naming, imports, уровень комментариев.
- **JSON API** — поля **snake_case**; ошибки через **`UserMessageSource`** / i18n bundles (`messages_ru` + `messages_en` парами).
- **Локализация API** — `app.locale` / `APP_LOCALE`; не хардкодить тексты ошибок в resources.
- **Комментарии** — только для неочевидной бизнес-логики; код преимущественно self-explanatory.
- **CHANGELOG** — значимые изменения в `[Unreleased]` с датой UTC ([`CHANGELOG.md`](CHANGELOG.md)).

### Локальная разработка

- **PR gate на хосте:** `./gradlew buildIntegrity`, unit/H2-тесты — без live stack.
- **Live stack:** Docker Compose + Ansible — [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md).
- **QEMU (Windows):** `deploy/qemu/`, `scripts/qemu-*.ps1` — **не в Git** (`.gitignore`); могут оставаться локально у разработчика. Агент **не ссылается** на них как на часть репозитория.

### PowerShell-скрипты (`scripts/*.ps1`)

- **ASCII-only** в `.ps1` (совместимость PS 5.1).
- **`Write-Host`** для служебного вывода оркестратора; не `Write-Output` в функциях, возвращающих `$state` (иначе `Object[]`).

### Playwright / US9 acceptance

- Tier manifest: [`tests/e2e-web/playwright-tiers.json`](tests/e2e-web/playwright-tiers.json).
- Запуск: см. [`tests/e2e-web/README.md`](tests/e2e-web/README.md) — env `PLAYWRIGHT_BASE_URL`, `KORUS_API_URL` на живой стек.
- Селекторы: **`data-testid`**, `#u`/`#p` — не locale-specific labels.

### Git

- Коммиты **только по запросу** пользователя.
- Не коммитить секреты (`.env`, vault plaintext).

### Superpowers (Cursor skills)

Проект использует [cursorpowers](https://github.com/kumekay/cursorpowers) (форк [obra/superpowers](https://github.com/obra/superpowers)) **на уровне репозитория** — рядом со **speckit-*** в `.cursor/skills/`. Мостовой skill: `.cursor/skills/korus-agent-workflow/SKILL.md`.

**Первая установка (Windows):**

```powershell
# Clone vendor (ignored by git). If corporate git proxy blocks GitHub, bypass for this command:
git -c http.proxy= -c https.proxy= clone https://github.com/kumekay/cursorpowers.git .cursor/superpowers

# Create junctions: .cursor/skills/superpowers-<name> -> .cursor/superpowers/skills/<name>
.\.cursor\install-superpowers.ps1
```

**Обновление skills:**

```powershell
Set-Location .cursor/superpowers
git -c http.proxy= -c https.proxy= pull
Set-Location ../..
.\.cursor\install-superpowers.ps1
```

**Удаление junctions:** `.\.cursor\install-superpowers.ps1 -Uninstall`

После install/update — **новая сессия агента** (или перезапуск Cursor), чтобы skills подхватились.

| Набор | Префикс | Когда использовать |
|-------|---------|-------------------|
| **Spec-kit** | `speckit-*` | Фичи в `specs/`: specify → plan → tasks → implement; constitution, analyze, checklist |
| **Superpowers** | `superpowers-*` | Brainstorming, TDD, systematic-debugging, writing/executing-plans, code-review, git-worktrees, subagent-driven-dev |
| **Мост** | `korus-agent-workflow` | Выбор между наборами + ограничения проекта (русский, minimal diff) |

**Сосуществование:** spec-kit — обязательный pipeline для tracked features; superpowers — инженерная дисциплина (TDD, отладка, планы вне spec-kit).

Альтернатива (user-level, не для этого репо): `~/.cursor/superpowers/.cursor/install.sh` из upstream INSTALL.md.

---

## 5. Проектирование новых функций

### Spec-kit workflow (обязательный для фич)

```
/speckit.specify → /speckit.plan → /speckit.tasks → /speckit.implement
```

Артефакты в `specs/<NNN-feature>/`: `spec.md`, `plan.md`, `tasks.md`, `research.md`, `contracts/`, `quickstart.md`.

Текущий активный plan: **`specs/007-platform-stage-readiness/plan.md`** (ops tail T601–T607).

### Перед реализацией

1. Проверить **constitution** и **ROADMAP** ([`docs/ROADMAP_EPICS.md`](docs/ROADMAP_EPICS.md)).
2. Определить контракт: OpenAPI annotation / NATS subject → обновить [`docs/NATS_SUBJECTS_INTEROP.md`](docs/NATS_SUBJECTS_INTEROP.md).
3. Если затрагивает persistence — dual-TTL, audit, export reader ([`docs/RETENTION_AND_DEEP_ARCHIVE.md`](docs/RETENTION_AND_DEEP_ARCHIVE.md)).
4. Если новый env — документировать в `application.properties` / README / quickstart.
5. Если deployment split — ADR + hotplug contract.

### Hexagonal / новый REST

- Write-path через `*ApplicationService` + port, не JDBC в `*Resource`.
- Контрольный вопрос: «класс тестируется без Tomcat?» → domain/application; иначе adapter.
- Jersey: отдельные class-level `@Path` для nested resources (conference, export) — не терять endpoints в `ChatResource`.

### Параллельная разработка

См. [`docs/PARALLEL_DEVELOPMENT.md`](docs/PARALLEL_DEVELOPMENT.md) — дробить PR по ресурсам; миграции Flyway мержить первыми.

---

## 6. Требования к тестированию

| Уровень | Требование |
|---------|------------|
| **PR gate** | `./gradlew buildIntegrity` (как CI [`.github/workflows/ci.yml`](.github/workflows/ci.yml)) |
| **Unit** | JUnit 5 для новой бизнес-логики |
| **Repository** | H2 integration tests для SQL/предикатов |
| **Workers** | тесты core logic (JSON, chunking, filters) |
| **API parity** | Playwright tiers на живом стеке (см. `tests/e2e-web/README.md`) |
| **Smoke** | канонические сценарии — [`scripts/SMOKE_INDEX.md`](scripts/SMOKE_INDEX.md) |
| **E2EE** | `:modules:core-api:test --tests "*Mls*"`; `e2ee-capabilities.spec.ts` |
| **Deploy** | `scripts/smoke-deploy-acceptance.sh` (CI nightly) |

### US9 два контура (spec 004)

| Контур | Когда | Критерий |
|--------|-------|----------|
| **Inner** | каждая правка test/UI | tier green, `< 2 min` при живом стеке |
| **Outer** | перед merge / sign-off | smoke + **34/34** Playwright + `runtime-gate-report.md` |

Preflight fail → **не** гонять full suite. Outer orchestrator → **blocked**, не blind retry.

---

## 7. Требования к документации

### Обязательно обновлять при изменении функциональности

| Документ | Когда обновлять |
|----------|-----------------|
| **`CHANGELOG.md`** | любое заметное поведение/API |
| **`README.md`** | новые модули, команды, порты |
| **`specs/.../tasks.md`** | отмечать `[x]` выполненные задачи |
| **`docs/NATS_SUBJECTS_INTEROP.md`** | новые/изменённые NATS subjects |
| **`docs/db/FLYWAY_AND_SCHEMA.md`** | новые миграции (если меняется модель) |
| **`docs/PORTS_MATRIX.md`** | новые порты |
| **`scripts/SMOKE_INDEX.md`** | новые smoke-скрипты |
| **contracts в `specs/*/contracts/`** | изменение acceptance criteria |
| **`runtime-gate-report.md`** | после green Playwright на live stack |
| **`ops-signoff-log.md`** | ops/security gates |
| [`docs/index.html`](docs/index.html) | product deck (GitHub Pages); rebuild: `python scripts/presentation/build.py` |
| [`docs/README.md`](docs/README.md) | индекс документации; deck vs legacy |

### Spec-kit артефакты по фичам

**Активные:**

| Spec | Назначение |
|------|------------|
| [`specs/018-product-deck/`](specs/018-product-deck/) | Product deck — `docs/index.html`, honesty gate, GitHub Pages |
| [`specs/015-live-server-ops-backlog/`](specs/015-live-server-ops-backlog/) | **Deferred ops registry** — live-server задачи (Sep 2026+); не в списках доработок агента |
| [`specs/011-korus-cloud-platform/`](specs/011-korus-cloud-platform/) | Korus Cloud Cells — Phase 0–1 closed; Phase 2+ ops → spec 015 |
| [`specs/007-platform-stage-readiness/`](specs/007-platform-stage-readiness/) | Ops/stage gates T601–T607 (eng. ✅; ops → spec 015) |

**Закрыты (engineering):**

| Spec | Назначение |
|------|------------|
| [`specs/010-presentation-gaps-closure/`](specs/010-presentation-gaps-closure/) | §4 presentation gaps — eng. closed; Phase B ops Sep 2026+ |
| [`specs/008-repository-cleanup/`](specs/008-repository-cleanup/) | Тотальная гигиена репо (closed 2026-06-15) |
| [`specs/009-platform-modules/`](specs/009-platform-modules/) | indexer + bot-delivery + Bot API MVP (closed 2026-06-15) |

**Архив (001–006):** [`specs/archive/README.md`](specs/archive/README.md) — living docs в [`docs/parity/`](docs/parity/), [`docs/contracts/`](docs/contracts/), [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md).

### Architecture & ADR

| Документ | Назначение |
|----------|------------|
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | принципы проекта, governance |
| [`docs/ARCHITECTURE_CORE_PACKAGES.md`](docs/ARCHITECTURE_CORE_PACKAGES.md) | hexagonal layers, migration phases |
| [`docs/E2EE_ARCHITECTURE.md`](docs/E2EE_ARCHITECTURE.md) | E2EE/MLS модель |
| [`docs/adr/ADR-e2ee-mls-library.md`](docs/adr/ADR-e2ee-mls-library.md) | выбор hybrid MLS (Web Crypto + server) |
| [`docs/adr/ADR-hotplug-deployment-split.md`](docs/adr/ADR-hotplug-deployment-split.md) | hot-plug workers |
| [`docs/proposals/constitution-v1.1-hotplug-bounded-exception.md`](docs/proposals/constitution-v1.1-hotplug-bounded-exception.md) | обоснование v1.1 constitution |

### Plans & roadmap

| Документ | Назначение |
|----------|------------|
| [`docs/ROADMAP_EPICS.md`](docs/ROADMAP_EPICS.md) | эпики после базового ТЗ |
| [`docs/README.md`](docs/README.md) | индекс документации, deck vs legacy |
| [`docs/plans/README.md`](docs/plans/README.md) | индекс детальных планов |
| [`docs/RETENTION_AND_DEEP_ARCHIVE.md`](docs/RETENTION_AND_DEEP_ARCHIVE.md) | ретенция, purge, Solr |

### Review & sign-off

| Документ | Назначение |
|----------|------------|
| [`docs/review/code-review-2026-05-23.md`](docs/review/code-review-2026-05-23.md) | baseline tech debt |
| [`docs/review/e2ee-security-gate-signoff-2026-06-10.md`](docs/review/e2ee-security-gate-signoff-2026-06-10.md) | E2EE engineering checklist |
| [`docs/review/hotplug-governance-handoff-2026-05-24.md`](docs/review/hotplug-governance-handoff-2026-05-24.md) | hotplug sign-off process |
| [`docs/review/ops-signoff-log.md`](docs/review/ops-signoff-log.md) | ops/security sign-off matrix |

### Deploy & dev runtime

| Документ | Назначение |
|----------|------------|
| [`docs/DEV_STACK_PROFILES.md`](docs/DEV_STACK_PROFILES.md) | pilot/standard/enterprise, full-server vs dev-min |
| [`deploy/ansible/README.md`](deploy/ansible/README.md) | Ansible inventories, playbooks |
| [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md) | быстрый старт deploy |
| [`docs/CI_AND_REPO_HYGIENE.md`](docs/CI_AND_REPO_HYGIENE.md) | CI, Dependabot, smoke policy |
| [`tests/e2e-web/README.md`](tests/e2e-web/README.md) | Playwright tiers |

### Contracts (spec 004 examples)

| Contract | Назначение |
|----------|------------|
| [`docs/contracts/fast-acceptance-contract.md`](docs/contracts/fast-acceptance-contract.md) | US9 inner/outer acceptance |
| [`docs/contracts/playwright-gate-contract.md`](docs/contracts/playwright-gate-contract.md) | Playwright gate criteria |
| [`docs/contracts/e2ee-mls-contract.md`](docs/contracts/e2ee-mls-contract.md) | MLS API surface |
| [`docs/contracts/tls-deploy-contract.md`](docs/contracts/tls-deploy-contract.md) | stage/prod TLS |

**Правило:** при изменении поведения, затронутого contract/spec — обновить contract **в том же PR/коммите**, что и код. Tasks.md — отметить задачу выполненной.

---

## Dev runtime

**В репозитории:** сборка/тесты — `./gradlew buildIntegrity`; live stack — Docker Compose + Ansible ([`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md)). Профили: [`docs/DEV_STACK_PROFILES.md`](docs/DEV_STACK_PROFILES.md).

**Локально (не в Git):** QEMU-стек Windows (`deploy/qemu/`, `scripts/qemu-*.ps1`) — см. `.gitignore`.

---

## Сопровождение документации (чеклист агента)

После **каждой значимой задачи**:

1. Нужно ли обновить spec/tasks/contracts/quickstart?
2. Обновлены ли CHANGELOG, README, runtime-gate-report, ops-signoff?
3. Новые **User Preferences** → раздел ниже.
4. Новые **Project Learnings** → раздел ниже.
5. Кратко сообщить пользователю, какие документы изменены.

---

## User Preferences

Особенности взаимодействия, выявленные в ходе работы (обновлять при новых паттернах):

| Область | Предпочтение |
|---------|--------------|
| **Язык** | Ответы пользователю — **русский**; идентификаторы/код/логи — как в репозитории |
| **Коммиты** | По умолчанию — только по явной просьбе |
| **Scope** | Минимальный diff; не трогать unrelated code |
| **Runtime / live stack** | Docker Compose + Ansible (см. deploy/ansible). QEMU — локально, **не в Git** |
| **Presentation deck / sizing** | **Не использовать Pilot/Standard/Enterprise** в deck. Только **prod full** для sizing/TCO. **Dev-min** — lab compose. См. `scripts/presentation/module_sizing.py`, `METRIC_POLICY.md`. |
| **Presentation deck** | `python scripts/presentation/build.py` → `docs/index.html`; см. [`scripts/presentation/README.md`](scripts/presentation/README.md) |
| **Документация** | Не создавать markdown «просто так»; docs по запросу или в рамках spec-kit |
| **Тесты** | Полезные тесты только при реальном coverage; не trivial asserts |
| **Plan files** | Не редактировать `.cursor/plans/*.plan.md` без явного указания |
| **GitHub** | **Не ставить GitHub CLI (`gh`)**; push — `.\scripts\git-push.ps1`; PR — вручную через compare на github.com |
| **Stage/prod стенд** | **До сентября 2026 стенда не будет.** Ops-задачи — [`specs/015-live-server-ops-backlog/`](specs/015-live-server-ops-backlog/). E2E/smoke на live stack — compose/Ansible или локальный QEMU (вне Git). |
| **Live-server backlog** | **До Sep 2026 или явного распоряжения** — задачи из spec **015** **не выводить** в списки «доработать / next steps / waves». При обзоре статуса — **краткое напоминание**, что deferred ops-реестр есть (ссылка на 015). Исключение: пользователь явно просит ops/stage или конкретный T601/LSO-* |
| **Версия продукта** | Единая метка **`0.0.1-SNAPSHOT`** — рабочая болванка до первого релиза, **не** marketing semver (не `2.x`). Источник: `scripts/presentation/product_status.py`, Gradle `version`, `app.version`. Customer-facing — только **`docs/index.html`** (deck). |

---

## Project Learnings

Накопленные знания (обновлять при новых находках):

### Presentation / docs (2026-06-18)

- **Канон для заказчика:** `docs/index.html` (deck pipeline `scripts/presentation/`).
- **Индекс документации:** `docs/README.md`; корневой `README.md` — краткий обзор проекта.
- Legacy HTML и QEMU tooling **удалены из Git** (2026-06-18); QEMU только `.gitignore`.

### Presentation / sizing (2026-06-18)

- **Product deck:** prod full для sizing/TCO; dev-min — lab compose.
- **Версия в deck:** `0.0.1-SNAPSHOT` (болванка).

### Playwright / US9

- Tier manifest: `tests/e2e-web/playwright-tiers.json`.
- **34/34** outer gate (2026-06-16): `specs/002-web-client-server-parity/runtime-gate-report.md`.

### Architecture / code

- **Conference API**: отдельный `ChatConferenceResource` `@Path("/v1/chats/{chatId}/conferences")` — иначе Jersey теряет endpoints.
- **MLS cipher_suite**: VARCHAR(32) слишком узкий — миграция `V029__e2ee_cipher_suite_widen.sql`.
- **Playwright UI**: `data-testid=call-panel-toggle`, MLS-aware `uiSendMessage` (encrypted bubble OK).
- **PowerShell 5.1**: кириллица и `[OK]` в double-quoted strings ломает парсинг — ASCII + single quotes или plain text.

### Stage / prod timeline

- **Stage/prod хост недоступен до сентября 2026** (решение команды, 2026-06-15).
- **Deferred ops registry:** [`specs/015-live-server-ops-backlog/`](specs/015-live-server-ops-backlog/) — T601–T607, human sign-offs, live creds, multi-cell; агент **не включает** в списки доработок до Sep 2026+ или явного распоряжения ([`ops-live-server-deferred.mdc`](.cursor/rules/ops-live-server-deferred.mdc)).
- Spec **007** Phase 6: engineering closed; ops-строки зеркалятся в spec **015** (LSO-001…007).

### Открытые ops-гештальты

**См. реестр:** [`specs/015-live-server-ops-backlog/tasks.md`](specs/015-live-server-ops-backlog/tasks.md) — не дублировать в agent backlog lists.

Кратко: TLS/vault (US1), E2EE 8/8, hotplug sign-off, cloud A-pool, live integrations creds, formal load @ stage.

---

*Последнее существенное обновление AGENTS.md: 2026-06-18 (QEMU вне Git; legacy presentations удалены).*
