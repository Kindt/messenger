<!-- SPECKIT START -->
Current plan: `specs/004-deferred-phase2-closure/plan.md`
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
| Deploy | **Docker Compose**, **Ansible**, **QEMU** (Windows dev) |
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
│   ├── ansible/          # roles, playbooks (Linux, CI, QEMU guests)
│   ├── qemu/             # Windows dev: 2 Ubuntu VM, bootstrap, lib/
│   └── two-host/         # LAN: server host + web host
├── .cursor/
│   ├── skills/           # speckit-* (committed), superpowers-* (junctions), korus-agent-workflow
│   ├── superpowers/      # vendor clone cursorpowers (gitignored)
│   └── install-superpowers.ps1
├── korus-web/            # Docker: nginx + replicas web-client
├── scripts/              # smoke, stack-up, qemu-*, profiling
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
| `deploy/qemu/run/` | runtime-артефакты: логи, `inner-tier-status.json`, orchestrator state |
| `specs/00N-*` | feature specs (001 review, 002 parity, 003 deploy, 004 closure) |
| `.cursor/rules/` | правила агента (QEMU isolation, redeploy-monitor, speckit plan, chat-watch) |

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

### Windows dev host (QEMU isolation)

**На Windows-хосте запрещено** (см. [`.cursor/rules/qemu-host-isolation.mdc`](.cursor/rules/qemu-host-isolation.mdc)):

- `docker compose`, Ansible deploy на реальные targets, `full-stack-up.ps1`, host Docker stacks.

**Разрешено на хосте:**

- `.\scripts\qemu-up.ps1`, `qemu-redeploy.ps1`, `qemu-down.ps1`, `playwright-dev-loop.ps1`;
- `./gradlew buildIntegrity`, unit-тесты без live stack;
- браузер/Playwright на **`127.0.0.1:18080`** (API) и **`:19088`** (UI).

Runtime (Docker, Ansible, compose) — **внутри QEMU guests** `korus-server` / `korus-web`.

### PowerShell-скрипты (`deploy/qemu`, `scripts/*.ps1`)

- **ASCII-only** в `.ps1` (совместимость PS 5.1); русский текст — в JSON i18n ([`deploy/qemu/lib/plan-failure-i18n.json`](deploy/qemu/lib/plan-failure-i18n.json), `minute-report-i18n.json`).
- **`Write-Host`** для служебного вывода оркестратора; не `Write-Output` в функциях, возвращающих `$state` (иначе `Object[]`).
- **Не вызывать `qemu-down`** без явной просьбы пользователя; не убивать чужие QEMU.

### Playwright / US9 acceptance

- Inner loop: `.\scripts\playwright-dev-loop.ps1 -Tier <api|ui-*|all-inner>`.
- Outer gate (редко): `.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp`.
- Tier manifest: [`tests/e2e-web/playwright-tiers.json`](tests/e2e-web/playwright-tiers.json).
- Селекторы: **`data-testid`**, `#u`/`#p` — не locale-specific labels.
- Env: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:19088`, `KORUS_API_URL=http://127.0.0.1:18080`.

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
| **Мост** | `korus-agent-workflow` | Выбор между наборами + ограничения проекта (QEMU, русский, minimal diff) |

**Сосуществование:** spec-kit — обязательный pipeline для tracked features; superpowers — инженерная дисциплина (TDD, отладка, планы вне spec-kit). Ограничения QEMU/host Docker из [qemu-host-isolation](.cursor/rules/qemu-host-isolation.mdc) **перекрывают** generic-советы superpowers.

Альтернатива (user-level, не для этого репо): `~/.cursor/superpowers/.cursor/install.sh` из upstream INSTALL.md.

---

## 5. Проектирование новых функций

### Spec-kit workflow (обязательный для фич)

```
/speckit.specify → /speckit.plan → /speckit.tasks → /speckit.implement
```

Артефакты в `specs/<NNN-feature>/`: `spec.md`, `plan.md`, `tasks.md`, `research.md`, `contracts/`, `quickstart.md`.

Текущий активный plan: **`specs/004-deferred-phase2-closure/plan.md`**.

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
| **API parity** | Playwright tiers + full 26 на QEMU перед sign-off |
| **Smoke** | канонические сценарии — [`scripts/SMOKE_INDEX.md`](scripts/SMOKE_INDEX.md) |
| **E2EE** | `:modules:core-api:test --tests "*Mls*"`; `e2ee-capabilities.spec.ts` |
| **Deploy** | `scripts/smoke-deploy-acceptance.sh` (CI nightly) |

### US9 два контура (spec 004)

| Контур | Когда | Критерий |
|--------|-------|----------|
| **Inner** | каждая правка test/UI | tier green, `< 2 min` при живом стеке |
| **Outer** | перед merge / sign-off | smoke + 26/26 Playwright + `runtime-gate-report.md` |

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
| **`runtime-gate-report.md`** | после green Playwright на QEMU |
| **`ops-signoff-log.md`** | ops/security gates |
| **Этот `AGENTS.md`** | новые learnings / preferences (см. ниже) |

### Spec-kit артефакты по фичам

| Spec | Назначение |
|------|------------|
| [`specs/001-system-review-refactoring/`](specs/001-system-review-refactoring/) | profiling, hex tail, hot-plug indexer, code review |
| [`specs/002-web-client-server-parity/`](specs/002-web-client-server-parity/) | parity matrix, Playwright, HANDOFF, runtime gate |
| [`specs/003-docker-ansible-autotest/`](specs/003-docker-ansible-autotest/) | Docker+Ansible deploy, messaging smokes, CI |
| [`specs/004-deferred-phase2-closure/`](specs/004-deferred-phase2-closure/) | TLS, hex write-path, E2EE MLS, QEMU DX, **US9 fast acceptance** |

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
| [`docs/plans/README.md`](docs/plans/README.md) | индекс детальных планов (retention, e2ee, read receipts, …) |
| [`docs/RETENTION_AND_DEEP_ARCHIVE.md`](docs/RETENTION_AND_DEEP_ARCHIVE.md) | ретенция, purge, Solr |

### Review & sign-off

| Документ | Назначение |
|----------|------------|
| [`docs/review/code-review-2026-05-23.md`](docs/review/code-review-2026-05-23.md) | baseline tech debt |
| [`docs/review/e2ee-security-gate-signoff-2026-06-10.md`](docs/review/e2ee-security-gate-signoff-2026-06-10.md) | E2EE engineering checklist |
| [`docs/review/hotplug-governance-handoff-2026-05-24.md`](docs/review/hotplug-governance-handoff-2026-05-24.md) | hotplug sign-off process |
| [`specs/004-deferred-phase2-closure/ops-signoff-log.md`](specs/004-deferred-phase2-closure/ops-signoff-log.md) | ops/security sign-off matrix |

### Deploy & dev runtime

| Документ | Назначение |
|----------|------------|
| [`deploy/qemu/README.md`](deploy/qemu/README.md) | QEMU golden path, порты, troubleshooting |
| [`deploy/ansible/README.md`](deploy/ansible/README.md) | Ansible inventories, playbooks |
| [`docs/CI_AND_REPO_HYGIENE.md`](docs/CI_AND_REPO_HYGIENE.md) | CI, Dependabot, smoke policy |
| [`tests/e2e-web/README.md`](tests/e2e-web/README.md) | Playwright tiers, inner/outer loop |

### Contracts (spec 004 examples)

| Contract | Назначение |
|----------|------------|
| [`specs/004-deferred-phase2-closure/contracts/fast-acceptance-contract.md`](specs/004-deferred-phase2-closure/contracts/fast-acceptance-contract.md) | US9 inner/outer acceptance |
| [`specs/004-deferred-phase2-closure/contracts/playwright-gate-contract.md`](specs/004-deferred-phase2-closure/contracts/playwright-gate-contract.md) | Playwright gate criteria |
| [`specs/004-deferred-phase2-closure/contracts/e2ee-mls-contract.md`](specs/004-deferred-phase2-closure/contracts/e2ee-mls-contract.md) | MLS API surface |
| [`specs/004-deferred-phase2-closure/contracts/tls-deploy-contract.md`](specs/004-deferred-phase2-closure/contracts/tls-deploy-contract.md) | stage/prod TLS |

**Правило:** при изменении поведения, затронутого contract/spec — обновить contract **в том же PR/коммите**, что и код. Tasks.md — отметить задачу выполненной.

---

## Dev runtime (QEMU)

На **Windows host** runtime только через две QEMU VM:

| VM | Guest IP | Host ports |
|----|----------|------------|
| `korus-server` | 192.168.76.10 | 18080, 18081, 18082 |
| `korus-web` | 192.168.76.20 | 19088 |

```powershell
# Headless
.\scripts\qemu-up.ps1 -KeepDisks
.\scripts\qemu-stack-wait.ps1

# Inner loop
.\scripts\playwright-dev-loop.ps1 -Tier all-inner

# Outer gate (once)
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp
```

Graphical: `.\scripts\qemu-dev-up.ps1` → API http://127.0.0.1:18080, UI http://127.0.0.1:19088.

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
| **Коммиты** | По умолчанию — только по явной просьбе; **QEMU redeploy-цикл** — коммит после каждого fix+restart (см. `qemu-redeploy-monitor.mdc`) |
| **Scope** | Минимальный diff; не трогать unrelated code |
| **Runtime Windows** | **QEMU only** — не Docker/Ansible на хосте ([qemu-host-isolation](.cursor/rules/qemu-host-isolation.mdc)) |
| **Acceptance** | Inner tier (`playwright-dev-loop -Tier …`) **до** full orchestrator |
| **QEMU lifecycle** | Не `qemu-down` без запроса; не kill non-Korus QEMU |
| **Документация** | Не создавать markdown «просто так»; docs по запросу или в рамках spec-kit |
| **Тесты** | Полезные тесты только при реальном coverage; не trivial asserts |
| **Plan files** | Не редактировать `.cursor/plans/*.plan.md` без явного указания |

---

## Project Learnings

Накопленные знания (обновлять при новых находках):

### QEMU / dev stack

- **`qemu-up -KeepDisks`** часто поднимает UI, но **API containers на server guest не стартуют** — нужен `qemu-redeploy -ServerOnly` (~20–25 мин).
- **Exited(255)** docker после KeepDisks — auto-remediate триггерит server redeploy (probe в `Invoke-KorusQemuAutoRemediate.ps1`).
- **wsUrl mismatch**: web client embeds LAN IP; при смене IP хоста — `qemu-redeploy -WebOnly` или auto-remediate; smoke с `-ExpectWsHost` падает до fix.
- **Repo HTTP** (`repo.tgz` на `:18890`) — обрыв во время redeploy рвёт bootstrap; не redeploy пока cloud-init не завершился.
- Serial/bootstrap логи: guest **`/var/log/korus-bootstrap.log`**, host **`deploy/qemu/run/*-serial.log`**.
- **Redeploy-цикл агента:** monitor → diagnose → fix → `qemu-redeploy-monitored.ps1` → **commit**; stale lock при мёртвом stack; VM-death в wait-loop — не ждать HTTP вслепую (правило `qemu-redeploy-monitor.mdc`).

### Playwright / US9

- QEMU host ports: **19088** (UI), **18080** (API) — не 9088/8080.
- **`playwright-dev-loop.ps1`**: `$ErrorActionPreference Stop` + stderr Node → ложный fail; оборачивать `npx` в `Continue`.
- **Grep tier manifest**: паттерн `|media capabilities` (без `include`) ломает regex — использовать `media capabilities include` + `conference-rtc.spec.ts` в api tier.
- **`Register-PlanFailure` / orchestrator**: `Write-Output` в `Emit-PlanChatTick` ломает `$state` (массив вместо hashtable) → только `Write-Host`.
- **26/26** достижимо на живом QEMU (2026-06-12); MLS active required для e2ee-browser-roundtrip.

### Architecture / code

- **Conference API**: отдельный `ChatConferenceResource` `@Path("/v1/chats/{chatId}/conferences")` — иначе Jersey теряет endpoints.
- **MLS cipher_suite**: VARCHAR(32) слишком узкий — миграция `V029__e2ee_cipher_suite_widen.sql`.
- **Playwright UI**: `data-testid=call-panel-toggle`, MLS-aware `uiSendMessage` (encrypted bubble OK).
- **PowerShell 5.1**: кириллица и `[OK]` в double-quoted strings ломает парсинг — ASCII + single quotes или plain text.

### Открытые ops-гештальты (не automation)

- Stage/prod **TLS + vault** (US1) — нужен real host.
- **E2EE formal sign-off** (Product/Security/Ops) перед `MLS_STATUS=active` в prod.
- **Hotplug governance** — именованные подписи (`apply-hotplug-signoff.ps1`).
- Spec **003 T080–T084** — prod inventory scaffold.

---

*Последнее существенное обновление AGENTS.md: 2026-06-12 (Superpowers skills, US9 fast acceptance, QEMU learnings).*
