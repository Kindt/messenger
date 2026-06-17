# Spec 015 — Live-server ops backlog (deferred registry)

**Status:** living registry (дополняется по мере появления ops-задач)  
**Effective:** 2026-06-17  
**Unblock:** **September 2026+** или **явное распоряжение** пользователя/команды на работу со stage/prod/live creds

---

## Назначение

Единый реестр задач, которые **нельзя выполнить или принять** без:

- реального stage/prod хоста (FQDN, TLS, vault);
- production-like инфраструктуры (2+ Cells, platform LB, formal load @ scale);
- live credentials внешних систем (не mock/QEMU);
- human sign-off (Security/Product/Ops).

Инженерные deliverables (скрипты, playbooks, contracts, QEMU smokes) для этих задач **уже в репо** — см. исходные specs 007, 010, 011, 014.

---

## Правило для AI-агентов

**До сентября 2026 или особого распоряжения:**

1. **Не включать** пункты из [`tasks.md`](tasks.md) в списки «что доработать», «следующие шаги», «backlog wave», «рекомендуемые PR».
2. **Не предлагать** stage/prod deploy, smokes на реальном FQDN, k6 baseline на stage, formal E2EE sign-off как блокер текущей инженерии.
3. **Напоминать кратко** (1–2 предложения), что deferred ops-реестр существует и где он: **`specs/015-live-server-ops-backlog/`**.
4. **Исключение:** пользователь явно спрашивает про stage/prod/ops или даёт распоряжение «делаем T601» / «готовим stage».

**QEMU (`127.0.0.1:18080` / `:19088`)** — не live-server; acceptance остаётся на QEMU.

---

## Что считается live-server задачей

| Критерий | Пример |
|----------|--------|
| Real FQDN + HTTPS/WSS | T601–T602, T607, T210 |
| Vault secrets on target host | VAPID, coturn, DB prod |
| Human signatures | E2EE 8/8, hotplug 3 signers, T01134 |
| Multi-cell / prod topology | T01124 (2 Cells), platform LB ADR |
| Live external backends | T01431 integrations `INTEGRATIONS_BACKEND_MODE=live` |
| Formal load @ production scale | k6 20% peak on stage, L6 10k soak (non-QEMU) |
| PG RLS apply on shared prod PG | post-V039 policy rollout on stage |

**Не live-server:** `buildIntegrity`, unit/H2, QEMU Playwright, guest smokes, engineering scaffolds, mock integrations gate.

---

## Связанные документы

| Документ | Роль |
|----------|------|
| [`tasks.md`](tasks.md) | Живой список deferred задач (дополнять здесь) |
| [`specs/007-platform-stage-readiness/`](../007-platform-stage-readiness/) | Источник T601–T607 |
| [`specs/010-presentation-gaps-closure/`](../010-presentation-gaps-closure/) | Phase B ops overlap |
| [`specs/011-korus-cloud-platform/`](../011-korus-cloud-platform/) | Cloud cells ops tail |
| [`docs/review/ops-signoff-log.md`](../../docs/review/ops-signoff-log.md) | Sign-off matrix |
| [`.cursor/rules/ops-live-server-deferred.mdc`](../../.cursor/rules/ops-live-server-deferred.mdc) | Cursor rule (always apply) |

---

## Как дополнять реестр

1. Новая ops-задача с live-server блокером → строка в [`tasks.md`](tasks.md) с ID `LSO-NNN`.
2. Ссылка на исходный spec/task ID (007/010/…).
3. Кратко: блокер, eng. артефакт (если есть), критерий закрытия.
4. **Не дублировать** в других `tasks.md` как «открытая инженерная работа» — только pointer на 015.
