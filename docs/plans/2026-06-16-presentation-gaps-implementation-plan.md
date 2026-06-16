# План закрытия незавершённых блоков продуктовой презентации

**Дата:** 2026-06-16  
**Источник:** [`product_presentation.html`](../../product_presentation.html) v2.3+, [`docs/PRODUCT_PRESENTATION.md`](../PRODUCT_PRESENTATION.md)  
**Статус:** `active` — инженерный backlog после spec 007 + wave 4 (FR-OPT-08)

---

## 1. Сводка по статусам в презентации

| Метка | Смысл | Кол-во блоков (ключевые) |
|-------|--------|--------------------------|
| **Реализовано** | Доступно на QEMU / в коде | §4 core, 24 кейса КУ-01…КУ-23, Pilot/Standard |
| **Частично** | Код есть; ops / sign-off / prod config | TLS, E2EE, Push, звонки, **Bot API MVP**, §7, §13 GDPR, traceability |
| **Запланировано** | Нет публичной поставки | SSO, Live HLS, mobile/desktop, FR-OPT-09 full, Bot API long-poll |

**Расхождение код ↔ презентация (обновлено v2.5.2):**

- **FR-OPT-08 dedup** — реализован в коде и презентации (`V031`, wave 4).
- **Bot API MVP** — spec 009 closed; презентация → **Частично** (long-poll, pin/ban — roadmap).
- **§12 в HTML** — «Интеграции»; infra optimization — в §9/§10 и резюме §1.

**Stage/prod:** реальный стенд **не раньше сентября 2026** — P0 ops отложен; acceptance на **QEMU** (см. `AGENTS.md`).

---

## 2. Блоки «Частично» — план закрытия

### P0 — Ops / stage (блокирует prod go-live; **стенд с сентября 2026**)

| ID | Блок презентации | Задачи | Владелец | Артефакты / критерий |
|----|------------------|--------|----------|----------------------|
| **P0-1** | Prod HTTPS, Vault (§7, КУ-24, traceability TLS) | T601–T602, T607 spec 007 | Ops | `inventory/stage/`, `smoke-tls-redirect.ps1`, `ops-signoff-log` US1 |
| **P0-2** | E2EE prod gate (§7, КУ-20, A23–A24) | T603, T606; 8/8 checklist | Security/Product | `e2ee-security-signoff-packet`, staging MLS rows |
| **P0-3** | Hotplug governance (§8 §6.X) | T605 | Architecture/Product/Ops | `apply-hotplug-signoff.ps1` |
| **P0-4** | Formal load test (§10, §11) | T604 full k6 на stage; 20% peak soak | Ops/QA | `k6-pilot-baseline.json` → обновление §10.2.1 |

**Скрипты готовы:** `stage-readiness-checklist.ps1`, `run-k6-qemu-baseline.ps1`.

### P1 — Инженерия prod-readiness (можно в репо параллельно P0)

| ID | Блок | Задачи | Spec / план | Критерий «Реализовано» в презентации |
|----|------|--------|-------------|--------------------------------------|
| **P1-1** | Web Push prod (§4, A25, §12 Web Push) | VAPID в vault, push-worker prod compose, smoke | spec **008** или 007 tail | `smoke-push-worker-qemu.ps1` green ✅; prod VAPID — ops |
| **P1-2** | TURN / звонки за NAT (§4, КУ-08, A21–A22) | coturn Ansible overlay, `korus-web` turn env | deploy 003 | `smoke-turn-qemu.ps1 -GuestOnly` green ✅; host `:3478` — restart web VM |
| **P1-3** | Preview worker (traceability §16) | Добавить в prod/pilot compose profile | 003 / ops | `smoke-preview-worker-qemu.ps1` green ✅ |
| **P1-4** | File proxy resize (traceability §15) | Hot-plug или sidecar в compose | ADR optional | ✅ embedded `/resize` + `smoke-file-resize.ps1` |
| **P1-5** | Security timing (§24) | `audit-timing.ps1` TTFB + GET chat normalization | plan 04 | ✅ PASS ~0.2% delta on QEMU (2026-06-16) |
| **P1-6** | GDPR export completeness (§13, КУ-21, A20) | `EXPORT_REQUIRED_FIELDS` strict + admin UI indicator | plan 03 + legal | ✅ guide `completeness_policy` + smoke; strict prod — ops |

### P1b — Синхронизация презентации с кодом

| ID | Задача | Действие |
|----|--------|----------|
| **P1b-1** | FR-OPT-08 | Обновить `PRODUCT_PRESENTATION.md`, build scripts, HTML: FR-OPT-08 → **Реализовано**; §9 dedup row |
| **P1b-2** | FR-OPT-09 | Оставить **Частично** (scaffold `DB_SHARD_JDBC_URL`); full router — отдельный ADR |
| **P1b-3** | Резюме §1 | Убрать «dedup» из «Не в поставке»; добавить в «реализовано wave 4» |

---

## 3. Блоки «Запланировано» — план реализации

### P2 — Интеграции (§12, §9, КУ-25/27, A32)

| ID | Фича | MVP scope | Оценка | Зависимости |
|----|------|-----------|--------|-------------|
| **P2-1** | **Bot API** | REST MVP ✅ (spec 009); long-poll, deleteMessage/pin/ban — backlog | — | `smoke-bot-api.ps1` green на QEMU |
| **P2-2** | **SSO OIDC** | Keycloak identity broker template + admin doc | 1–2 нед. | ✅ runbook + `keycloak-enable-identity-provider.sh`; live IdP — ops |
| **P2-3** | **LDAP/AD** | Keycloak user federation playbook | 1 нед. | P2-2 |
| **P2-4** | **Batch replay** (traceability) | Довести export-replay до non-stub policy | 2 нед. | ✅ `EXPORT_REPLAY_REQUIRE_JDBC` + smoke |

**Рекомендация:** spec **008-bot-api-sso** (speckit) перед кодом P2-1.

### P3 — Медиа и клиенты (долгий горизонт)

| ID | Фича | Примечание |
|----|------|------------|
| **P3-1** | Live HLS (§28, КУ-26, A33) | Отдельный media stack; не смешивать с mesh RTC |
| **P3-2** | SFU >20 участников (§29) | coturn + SFU ADR |
| **P3-3** | Mobile iOS/Android (A34) | **Вне репозитория** — отдельный продукт |
| **P3-4** | Desktop client | **Вне репозитория** или Electron spike |

### P4 — Enterprise scale (§10 500k/1M, FR-OPT-09)

| ID | Задача | Статус |
|----|--------|--------|
| **P4-1** | Org-based PG sharding router | Scaffold ✅; Phase A: 2 shards ADR |
| **P4-2** | Load test 100k tier | После P0-4 на stage |
| **P4-3** | Citus / app router | Quarter+; Enterprise only |

---

## 4. Рекомендуемый порядок спринтов

```mermaid
flowchart LR
  subgraph sprintA [Sprint_A_ops]
    P0_1[TLS_Vault]
    P0_2[E2EE_signoff]
    P0_4[k6_stage]
  end
  subgraph sprintB [Sprint_B_prod]
    P1_1[Web_Push]
    P1_2[TURN]
    P1b[Presentation_sync]
  end
  subgraph sprintC [Sprint_C_integrations]
    P2_1[Bot_API]
    P2_2[SSO]
  end
  sprintA --> sprintB
  sprintB --> sprintC
  P0_3[Hotplug] -.-> sprintA
```

| Спринт | Цель | Закрывает в презентации |
|--------|------|-------------------------|
| **A** (ops, 1–2 нед.) | Stage green + sign-offs | TLS, E2EE, load test baseline, hotplug |
| **B** (eng, 2–3 нед.) | Prod peripherals | Push, TURN, preview; FR-OPT-08 в HTML |
| **C** (eng, 3–4 нед.) | §12 integrations | Bot API MVP, SSO playbook |
| **D** (по запросу) | Compliance + media | GDPR strict, Live/SFU |

---

## 5. Spec-kit mapping

| Spec | Scope |
|------|-------|
| **007** (закрыт eng.) | Phase 6 ops — T601–T607 |
| **008** (closed) | Repository cleanup |
| **009** (closed) | Bot API MVP + indexer hot-plug |
| **004** US1/US6/US7 | Ops sign-off matrix (не дублировать) |

---

## 6. Критерий «презентация закрыта» для заказчика

1. Все строки traceability §8 — **Реализовано** или явно **Запланировано / вне репо** с датой roadmap.
2. Нет **Частично** без footnote «что осталось» и владельца (ops vs eng).
3. §1 резюме совпадает с `runtime-gate-report.md` и последним load test JSON.
4. Playwright outer gate green после каждого спринта B/C.

---

## 7. Следующий шаг (немедленно)

1. **P1b + v2.5.2:** синхронизация Bot API MVP в презентации — ✅ 2026-06-15.
2. **Spec 010/011:** engineering closure — ✅ 2026-06-16 (`tasks.md`, Phase B/2+ → Sep 2026+).
3. **P1 (QEMU):** Push/TURN/preview smokes green; multi-org Cell smoke — ✅.
4. **P1-5 timing:** ✅ TTFB audit PASS (~0.2%); normalization 220ms + not-found padding.
5. **P1-6 GDPR export:** ✅ admin `completeness_policy` + smoke; `EXPORT_COMPLETENESS_STRICT` prod — ops.
6. **Inner gate:** ✅ `playwright-dev-loop -Tier all-inner` (2026-06-16).
7. **Outer gate (T110):** ✅ **33/33** Playwright on QEMU (2026-06-16).
8. **P1-4 file resize:** ✅ `GET /v1/files/{id}/resize` embedded + smoke (2026-06-16).
9. **P1b presentation sync:** ✅ v2.5.3 HTML + traceability (2026-06-16).
10. **P2-2 SSO:** ✅ Keycloak broker template + runbook (IdP creds — ops).
11. **P2-4 batch replay:** ✅ `EXPORT_REPLAY_REQUIRE_JDBC` + non-stub smoke.
12. **P0 ops:** T601–T607 — backlog до Sep 2026.

Связанные документы: [`2026-06-15-unfinished-development-plan.md`](2026-06-15-unfinished-development-plan.md), [`ROADMAP_EPICS.md`](../ROADMAP_EPICS.md) §8.
