# План незавершённых разработок Korus Messenger

**Дата:** 2026-06-15  
**Статус:** `completed` (engineering sprint 007 closed 2026-06-15; ops backlog §2–4, §9.6 Phase 6–7)  
**Spec:** [`specs/007-platform-stage-readiness/`](../specs/007-platform-stage-readiness/)  
**Источники:** `docs/ROADMAP_EPICS.md`, `docs/PRODUCT_PRESENTATION.md`, `specs/004-deferred-phase2-closure/ops-signoff-log.md`, `AGENTS.md`, `docs/ARCHITECTURE_CORE_PACKAGES.md`, `docs/plans/2026-06-15-infra-optimization-design.md`

---

## 1. Резюме состояния

| Контур | Инженерный код | Автотесты / QEMU | Prod / ops sign-off |
|--------|----------------|------------------|---------------------|
| Базовое ТЗ + parity (002) | ✅ | ✅ 30/30 Playwright | ⏳ operator optional |
| Deploy Ansible/Docker (003) | ✅ | ✅ CI + guest smokes | ⏳ stage/prod TLS |
| Spec 004 closure (US1–US9) | ✅ | ✅ inner + outer gate | ⏳ TLS, E2EE, hotplug |
| Infra optimization (006) Waves 1–3 | ✅ | ✅ T311 guest gate | ⏳ load test matrix |
| E2EE/MLS hybrid (006 plan) | ✅ dev/QEMU | ✅ Playwright + unit | ⏳ 8/8 security gate |
| Hexagonal tail | частично | ✅ unit/H2 | 🔶 фазы 2b+ |

**Важно:** в `specs/*/tasks.md` нет открытых `[ ]` — «незавершённое» = **ops-гейты**, **отложенные эпики**, **инженерный хвост** и **продуктовые фичи из презентации**, не закрытые кодом.

---

## 2. Блок A — Prod / Ops (блокирует production rollout)

Приоритет: **критический** для выхода в stage/prod. Требует **реального хоста**, не только QEMU.

### A1. Stage/prod TLS + Vault (spec 004 US1)

| # | Задача | Владелец | Артефакты |
|---|--------|----------|-----------|
| A1.1 | DNS → stage host | Ops | inventory `stage/` |
| A1.2 | `ansible-vault encrypt group_vars/vault.yml` | Ops | `deploy/ansible/inventory/prod/` |
| A1.3 | `ansible-playbook -i inventory/stage playbooks/site.yml` | Ops | `contracts/tls-deploy-contract.md` |
| A1.4 | `smoke-tls-redirect.ps1` с реальными `-HttpUrl`/`-HttpsUrl` | Ops/QA | `scripts/smoke-tls-redirect.ps1` |
| A1.5 | Prod: `ansible-playbook ... --tags tls_smoke` | Ops | role `korus_smoke` |

**Критерий готовности:** HTTPS/WSS для пользователей; секреты не в git; sign-off в `ops-signoff-log.md` US1.

### A2. E2EE formal sign-off (spec 004 US7)

Инженерно: MLS active в dev, Playwright `e2ee-capabilities` green. **Prod `MLS_STATUS=active` запрещён** до human sign-off.

| # | Проверка | Статус |
|---|----------|--------|
| A2.1 | ADR hybrid (T130) | doc ✅ / Product+Eng ⏳ |
| A2.2 | `/plaintext-preview` → 403 при MLS | auto ✅ / Security ⏳ |
| A2.3 | Client без plaintext-preview при MLS | code review ⏳ |
| A2.4–A2.6 | NATS consumer, migrate-batch, admin status на staging | Ops ⏳ |
| A2.7 | Legacy scheme unchanged | ✅ |
| A2.8 | Playwright formal QA sign | ⏳ |

**Критерий:** 8/8 в `ops-signoff-log.md` + подписи.

### A3. Hotplug governance (spec 004 US6)

| Роль | Статус |
|------|--------|
| Architecture Owner | ⏳ |
| Product Owner | ⏳ |
| Ops/SRE | ⏳ |

**Действие:** `.\scripts\apply-hotplug-signoff.ps1` после согласования имён.

### A4. Load test matrix (infra design § Validation gate)

Не сделано после spec 006:

- k6/Locust @ 20% peak для Pilot / Standard / Enterprise
- Baseline метрик: p95 REST, p95 WS deliver, cache hit rate, PG connections, indexer lag
- Обновление `PRODUCT_PRESENTATION.md` §10.2 **измеренными** числами (§12 в презентации закрыт для FR-OPT 01–07; load test — при go-live)

---

## 3. Блок B — Инженерный хвост (код в репо, без prod-блокера)

Приоритет: **высокий** для устойчивости DX и снижения tech debt.

### B1. QEMU / outer gate hardening (уроки 2026-06-15)

| # | Проблема | Предлагаемое решение |
|---|----------|----------------------|
| B1.1 | wsUrl mismatch: Ansible обновляет `.env`, контейнеры не подхватывают | ✅ частично: `--force-recreate` в `korus_web`; проверить attach/turn overlays |
| B1.2 | Stale `qemu-redeploy-web.lock` блокирует auto-remediate | TTL lock + probe «процесс жив» или сброс lock при exit redeploy |
| B1.3 | SSH host key web guest меняется → plink fail | Auto-probe в `Get-KorusEd25519HostKey` при mismatch; обновление `ssh-hostkeys.ps1` |
| B1.4 | `ops-signoff-log` Playwright count 27 vs 30 | Синхронизировать с `runtime-gate-report.md` |

### B2. Hexagonal migration (ARCHITECTURE_CORE_PACKAGES)

| Фаза | Содержание | Статус |
|------|------------|--------|
| 2a Chat | Port + ApplicationService | ✅ |
| 2b Message | write-path через application + port | 🔶 частично (MessageService) |
| 2c User/Org/File | ports для register/create tail | 🔶 deferred low risk |
| 3 | Gradle `core-domain` split | 🔶 optional |

**Задачи:** продолжить write-path по ADR; `UserRepository.create` → port; миграции Flyway — один владелец на спринт.

### B3. Spec 006 — намеренно не сделано (Wave 4+)

Из design doc, этапы 8–9:

| Этап | Фича | Зависимости |
|------|------|-------------|
| 8 | File dedup (MinIO) | retention/export |
| 9 | PG sharding | load test, architecture ADR |

Также lab-only в Wave 2–3:

- Настоящий PG streaming replica (сейчас overlay `docker-compose.replica.yml`)
- SQL search routing на replica
- Динамическое число WS/pipeline реплик из Ansible (захардкожено `*-2`)

### B4. Worker / observability хвост

| # | Задача | Ссылка |
|---|--------|--------|
| B4.1 | Worker i18n: замена hardcoded строк | `docs/plans/05-worker-localization.md` |
| B4.2 | JFR profiling в prod JRE images | `docs/review/hotspots-2026-05-23.md` |
| B4.3 | deep-archiver/indexer HTTP metrics port в compose | profiling README |

### B5. Retention / export (roadmap средний приоритет)

| # | Задача | Smoke |
|---|--------|-------|
| B5.1 | Export перед aggressive purge | `smoke-export-replay-before-purge.ps1` |
| B5.2 | Solr validation после retention clear | `smoke-retention-solr-clear.ps1` |
| B5.3 | GDPR completeness export policy | юридическое согласование + код |

---

## 4. Блок C — Продуктовые фичи (запланировано в презентации / ТЗ)

Приоритет задаёт **продукт**; инженерная база частично есть.

### C1. Ближайший горизонт (3–6 мес.) — `PRODUCT_PRESENTATION.md` §9

| Направление | Статус | Зависимости |
|-------------|--------|-------------|
| Prod TLS + Vault | частично | Блок A1 |
| E2EE prod enable | частично | Блок A2 |
| Web Push production | частично | VAPID, push-worker prod |
| TURN для звонков | частично | coturn deploy, `korus-web` turn overlay |
| GDPR completeness export | запланировано | юристы + export-replay |

### C2. Средний горизонт (6–12 мес.)

| Направление | Статус |
|-------------|--------|
| Bot API | NATS subject есть; REST/webhook — нет |
| OpenMLS / external interop | Phase 3 deferred (`06-e2ee-mls.md`) |
| Hotplug worker split (indexer и др.) | ADR есть; sign-off pending |
| File Proxy resize | planned |
| SSO federation (Google/LDAP/OIDC) | Keycloak config |

### C3. Долгий горизонт (12+ мес.)

- Live-streaming (RTMP/HLS)
- Нативные mobile clients
- SFU для конференций >20 участников
- Proxy/VPN tunnel modes

### C4. Частично из traceability §8 презентации

| § ТЗ | Тема | Gap |
|------|------|-----|
| §15 | File proxy resize | не в prod |
| §16 | Preview worker | не в prod compose |
| §17 | Bot API | запланировано |
| §18 | Push mobile | web частично |
| §28 | Live-streaming | запланировано |
| §29 | RTC | mesh есть; SFU — planned |

---

## 5. Блок D — Документация и процесс

| # | Задача | Статус |
|---|--------|--------|
| D1 | `ops-signoff-log.md` — обновить outer gate 30/30, дата 2026-06-15 | ⏳ |
| D2 | `ROADMAP_EPICS.md` — добавить epic infra optimization completed | ⏳ |
| D3 | Design doc § Related changes — отметить выполненные чекбоксы | ⏳ |
| D4 | US2 runtime verification templates (`docs/review/us2-*`) | pending-runtime (legacy) |

---

## 6. Рекомендуемые волны работ (после spec 006)

### Волна P1 — Production readiness (4–8 недель, нужен stage host)

1. A1 TLS/Vault на stage  
2. A2 E2EE sign-off на staging  
3. A3 Hotplug sign-off  
4. A4 Load test pilot/standard baseline  
5. D1–D3 docs sync  

**Gate:** `ops-signoff-log` все таблицы подписаны; `runtime-gate-report` + stage smokes.

### Волна P2 — Platform hardening (2–4 недели, только репо)

1. B1 QEMU remediate (lock, host key, wsUrl e2e test)  
2. B3 replica lab → optional real replica spike  
3. B5 export/purge smokes в CI/guest gate  
4. B2 hex tail: Message/User write-path  

**Gate:** `buildIntegrity` + outer gate без ручного wsUrl fix.

### Волна P3 — Product expansion (по запросу заказчика)

Выбор из C1–C3: Bot API, TURN prod, Web Push, GDPR export, OpenMLS phase 3.

Каждая фича — новый `specs/00N-*` по speckit pipeline.

---

## 7. Матрица «что не начинать без…»

| Работа | Блокер |
|--------|--------|
| `MLS_STATUS=active` в prod | A2 sign-off |
| PG sharding | A4 load test + ADR |
| Hotplug split в prod | A3 sign-off |
| Enterprise tier prod sizing | A4 measured §10.2 |
| Bot API | Product API contract |

---

## 8. Следующий шаг (выбор приоритета)

**Выбрано (2026-06-15):** **D — гибрид** — P2 в репо первым, параллельно подготовка P1 на stage.

Детальный план спринта: **§9** ниже.

---

## 9. Гибридный спринт P2 + P1 prep (выбор D)

**Горизонт:** 4 недели (календарно гибко).  
**Принцип:** инженерия идёт в QEMU/репо **без ожидания stage**; ops-готовность stage **готовится параллельно** документами, inventory и чеклистами.

### 9.1 Два трека

| Трек | Владелец | Где выполняется | Gate |
|------|----------|-----------------|------|
| **T-P2** Platform hardening | Engineering (agent + dev) | Windows host + QEMU guests | `buildIntegrity` + outer gate без ручного wsUrl |
| **T-P1** Stage readiness prep | Ops + Engineering | stage host (когда доступен) + репо (inventory/docs) | US1 rows 1–3 ready-to-run; A2 staging checklist |

Треки **независимы** на неделях 1–2; на неделе 3–4 T-P1 может стартовать deploy, если stage host выдан.

### 9.2 Неделя 1 — стабилизация DX + ops prep kit

#### T-P2 (primary) — W1 ✅ 2026-06-15

| ID | Задача | Файлы / команды | Критерий | Статус |
|----|--------|-----------------|----------|--------|
| W1-B1.2 | Stale redeploy lock: TTL + PID probe | `Korus-QemuRedeployLock.ps1` | auto-remediate не блокируется мёртвым lock | ✅ |
| W1-B1.3 | SSH host key auto-refresh при mismatch | `Update-KorusGuestRepo.ps1` | plink validate + re-probe | ✅ |
| W1-B1.4 | Синхронизация 30/30 в ops-signoff | `ops-signoff-log.md` | counts = runtime-gate-report | ✅ |
| W1-D1 | Docs sync (D1–D3) | `ROADMAP_EPICS.md`, infra design § Related | чекбоксы актуальны | ✅ |

**Gate недели 1:** `./gradlew buildIntegrity` + `playwright-dev-loop -Tier all-inner`.

#### T-P1 (parallel, без stage) — W1 ✅ 2026-06-15

| ID | Задача | Артефакт | Критерий | Статус |
|----|--------|----------|----------|--------|
| W1-A1.prep | Stage inventory scaffold | `inventory/stage/README.md` | playbook dry-run документирован | ✅ |
| W1-A1.vault | Шаблон vault + encrypt runbook | `inventory/stage/group_vars/vault.yml.example` | ops может encrypt | ✅ |
| W1-A2.prep | E2EE staging checklist | `docs/review/e2ee-staging-checklist.md` | env + admin endpoints | ✅ |
| W1-A3.prep | Hotplug sign-off template | `docs/review/hotplug-signoff-request-template.md` | готово к apply-hotplug-signoff | ✅ |

### 9.3 Неделя 2 — outer gate automation + hex tail start

#### T-P2 — W2 ✅ 2026-06-15 (код/docs; outer gate re-verify optional)

| ID | Задача | Критерий | Статус |
|----|--------|----------|--------|
| W2-B1.1 | Host wsUrl probe | `scripts/test-korus-wsurl.ps1` | ✅ |
| W2-B1.5 | wsUrl mismatch → **Force** WebOnly redeploy | auto-remediate + plan orchestrator | ✅ |
| W2-B2.1 | Hex: edit ACL via MessageApplicationService | `MessageResource.edit` | ✅ |
| W2-B5.1 | Guest smoke export gate | `guest-smoke-platform-w2.sh` + SMOKE_INDEX | ✅ |

**Gate недели 2:** `qemu-plan-orchestrator.ps1 -SkipVmUp` **без ручных фиксов**.

#### T-P1 — W2 ✅ 2026-06-15

| ID | Задача | Критерий | Статус |
|----|--------|----------|--------|
| W2-A1.4 | TLS smoke runbook | `stage-tls-smoke-runbook.md` | ✅ |
| W2-A4.prep | k6 skeleton @ pilot | `scripts/load/` | ✅ |
| W2-A2.1 | E2EE security packet rows 1–3 | `e2ee-security-signoff-packet-2026-06-15.md` | ✅ |

### 9.4 Неделя 3 — replica lab + stage deploy (если host есть)

#### T-P2 — W3 partial ✅ 2026-06-15

| ID | Задача | Критерий | Статус |
|----|--------|----------|--------|
| W3-B3.1 | PG replica lab spike | `REPLICA_LAB.md`, `replica-lab.yml`, smokes | ✅ |
| W3-B2.2 | Hex: register → `UserRepositoryPort.createLocalUser` | AuthService + H2 test | ✅ |
| W3-B4.1 | Push worker preview i18n | `messages_worker_push_*` bundles | ✅ |

#### T-P1 (требует stage host)

| ID | Задача | Критерий |
|----|--------|----------|
| W3-A1.1–3 | DNS + vault encrypt + `site.yml` stage | ops-signoff US1 rows 1–3 ✅ |
| W3-A1.4 | Real TLS smoke | `smoke-tls-redirect.ps1` exit 0 |
| W3-A2.4–6 | MLS on staging: subscriber, migrate-batch, admin status | ops-signoff rows 4–6 |
| W3-A4.1 | Pilot load test baseline на stage или QEMU | metrics JSON | draft §10.2 numbers |

**Если stage host нет:** T-P1 неделя 3 сдвигается; T-P2 продолжается (B3, B4).

### 9.5 Неделя 4 — sign-off sprint + spec 007 closure

| ID | Трек | Задача | Gate |
|----|------|--------|------|
| W4-A2.8 | P1 | QA formal E2EE Playwright sign | US7 row 8 |
| W4-A3 | P1 | Hotplug `apply-hotplug-signoff.ps1` | US6 all roles |
| W4-A1.5 | P1 | Prod tls_smoke tag (если prod inventory готов) | US1 row 5 |
| W4-P2 | P2 | Full outer + load smoke in orchestrator optional tier | 30/30 + guest smokes |
| W4-spec | both | Закрыть `specs/007-platform-stage-readiness/` | tasks.md all [x] |

### 9.6 Spec-kit: предлагаемый `specs/007-*`

Один spec на гибрид (не два), чтобы не дробить gates:

```
specs/007-platform-stage-readiness/
  spec.md      — US: P2 hardening + P1 prep/deploy
  plan.md      — ссылка на §9 этого документа
  tasks.md     — W1–W4 IDs как чеклист
  contracts/
    qemu-outer-gate-contract.md   — no manual wsUrl fix
    stage-tls-prep-contract.md    — US1 ready-to-run
```

**Альтернатива:** продолжить `004` только для ops rows — хуже traceability для B1/B2.

### 9.7 Риски гибрида

| Риск | Митигация |
|------|-----------|
| Stage host задерживается | T-P2 не блокируется; W3-A* → backlog |
| wsUrl fix сложнее ожидаемого | W2 gate сдвигается; не начинать B3 sharding |
| E2EE human sign-off затягивается | `MLS_STATUS` остаётся `pilot`; prod не трогаем |
| Параллельные Flyway | один владелец миграций на спринт (constitution) |

### 9.8 Definition of Done (гибрид D)

- [x] Engineering W1–W3 T-P2 + T-P1 prep (`specs/007` Phases 1–5)
- [x] `ops-signoff-log`: Playwright **30/30**, дата 2026-06-15
- [x] Stage prep kit complete (US1 ready-to-run per `stage-tls-prep-contract.md`)
- [x] E2EE rows 1–3 packet ready (`e2ee-security-signoff-packet-2026-06-15.md`)
- [x] `CHANGELOG.md` [Unreleased] записи спринта
- [x] Outer gate green **без** ручного wsUrl / SSH key fix (T701c 2026-06-15)
- [ ] Load: pilot baseline JSON на QEMU/stage (T604 — k6 skeleton готов)
- [ ] Stage deploy + human sign-offs (T601–T607 — blocked: no stage host)

---

## 10. Закрытие документа №12 (2026-06-15)

**Инженерный контур гибрида D закрыт** в [`specs/007-platform-stage-readiness/`](../specs/007-platform-stage-readiness/).

| Блок | Состояние после закрытия |
|------|--------------------------|
| §2 A1–A4 Prod/Ops | ⏳ backlog (prep kit готов) |
| §3 B1–B5 инженерный хвост | частично закрыт (B1, B2 spike, B4 push); B3 lab Tier 1–2; B5 export guest optional |
| §4 C продукт | backlog без изменений |
| §9 гибрид W1–W3 | ✅ engineering |
| §9 W4 / Phase 6–7 | ⏳ ops |

**Следующий триггер:** выдача stage host → `inventory/stage/README.md` + `tasks.md` T601+.

**Этот файл** остаётся справочником inventory; активная работа — в `specs/007/tasks.md` и `ops-signoff-log.md`.

---

*Версия: 2026-06-15 (closed engineering). Ops updates — по мере stage/sign-off.*
