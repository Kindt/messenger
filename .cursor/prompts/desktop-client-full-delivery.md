# Desktop Java Client — полная поставка продукта (spec 031)

**Скопируй всё ниже** в чат агента или `/loop 2h @.cursor/prompts/desktop-client-full-delivery.md`

---

## Миссия

С **первого запуска** довести **кроссплатформенный Java desktop-клиент** Korus Messenger до **полностью рабочего продукта**: мульти-сервер, мульти-профиль, вложения в Downloads, настройки, обновления, parity с ядром/web-client по матрице.

**Не останавливайся** на одной волне, пока `product_delivery != COMPLETE` в `deploy/desktop/run/pipeline-status.json`.

---

## Старт (обязательно)

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -InitFullDelivery
```

Это создаёт статус: `wave=W0`, `role=Analyst`, `pipeline=D2`, `product_delivery=IN_PROGRESS`.

Затем **каждую итерацию** (конвейер **без остановок**):

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt -Continuous
```

**Не останавливайся** после одной роли. В той же сессии: роль → verify → `-Advance` → следующая роль. Параллельно готовь волну **N+1** (brief/plan/ux-spec), пока Engineer монтирует **N**.

См. [`continuous-conveyor.md`](../specs/031-desktop-java-client/design/continuous-conveyor.md).

Одна роль и пауза (legacy): `-EmitPrompt -SinglePhase`

---

## Глобальные правила

| # | Правило |
|---|---------|
| 1 | Читай `.cursor/skills/korus-desktop-orchestrator/SKILL.md` **до** любых правок |
| 2 | **ENGINEER запрещён** пока `plan_review != APPROVED` (pipeline D2/D3) |
| 3 | Runtime integration — **QEMU** `http://127.0.0.1:18080`, **не** host Docker |
| 4 | Минимальный diff; `webui/` только через UI orchestrator |
| 5 | Коммиты/push — **только по явной просьбе** пользователя |
| 6 | Не объявляй SUCCESS без **свежего** вывода verify-команд |
| 7 | Облачные агенты / cloud worktrees — **запрещены** (только локально) |

---

## Волны (строгий порядок)

| Wave | Содержание | Guide |
|------|------------|-------|
| **W0** | Gradle modules, auth, 1 server, shell | [`waves/W0.md`](../skills/korus-desktop-orchestrator/waves/W0.md) |
| **W1** | Multi-profile + multi-server + ContactRef | [`waves/W1.md`](../skills/korus-desktop-orchestrator/waves/W1.md) |
| **W2** | Messaging, attachments, offline queue | [`waves/W2.md`](../skills/korus-desktop-orchestrator/waves/W2.md) |
| **W3** | Capabilities, search, calls | [`waves/W3.md`](../skills/korus-desktop-orchestrator/waves/W3.md) |
| **W4** | Updates, jpackage, full parity | [`waves/W4.md`](../skills/korus-desktop-orchestrator/waves/W4.md) |

**Переход на следующую волну** — только после QA PASS текущей:

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -CompleteWave
```

---

## Конвейер ролей (pipeline D2, default)

```
ANALYST → ARCHITECT → DESIGNER → UX_EVALUATOR → PLAN_REVIEWER → ENGINEER → QA_VERIFIER → (CompleteWave) → next W*
```

**Конвейер:** роли идут **подряд в одной сессии**; Prepare-track (N+1) параллельно Build-track (N). См. `continuous-conveyor.md`.

| Role | Артефакт на диске | Skill |
|------|-------------------|-------|
| Analyst | `specs/031-desktop-java-client/artifacts/waves/{W}/desktop-brief.md` | `korus-desktop-role-analyst` |
| Architect | `.../desktop-plan.md` | `korus-desktop-role-architect` |
| PlanReviewer | `.../desktop-plan-review.md` | `korus-desktop-role-plan-reviewer` |
| Engineer | код в `modules/desktop-*` | `korus-desktop-role-engineer` |
| QaVerifier | `.../desktop-qa-evidence.md` | `korus-desktop-role-qa-verifier` |

Шаблоны: `.cursor/skills/korus-desktop-orchestrator/artifacts/*.template.md`

---

## Итерация (конвейер /loop)

```
A. .\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt -Continuous
B. BUILD: Engineer/QA — код + verify (fix until green)
C. PREPARE: пока build идёт или волна закрыта — Analyst/Designer на W+1
D. Gate PASS → -Advance / -SetUxReview PASS / -SetPlanReview Approved (авто, без вопроса пользователю)
E. QA PASS волны → -CompleteWave → сразу Analyst следующей волны
F. Повтор до product_delivery=COMPLETE. Пауза только при hard blocker.
```

**PlanReviewer APPROVED:**

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved -Advance
```

**CHANGES_REQUESTED:**

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview ChangesRequested -Rollback
```

---

## Definition of Done — весь продукт

Все условия **одновременно**:

1. `modules/desktop-client-sdk` и `modules/desktop-client` в `buildIntegrity` PASS
2. `specs/031-desktop-java-client/contracts/feature-parity-matrix.json` — все `status:required` → PASS (или DEFER + ADR в evidence)
3. Ручной сценарий:
   - 2 профиля на одной машине, данные изолированы
   - 2 сервера, контакты различимы
   - вложение в `{Downloads}/KorusMessenger/...`
   - проверка обновлений (manifest fetch + verify, установка optional в lab)
4. `deploy/desktop/run/pipeline-status.json`:

```json
{
  "wave": "W4",
  "role": "Done",
  "product_delivery": "COMPLETE",
  "plan_review": "APPROVED"
}
```

5. Краткий отчёт пользователю: волны, команды, известные DEFER (E2EE, admin).

---

## При FAIL

1. Capture — логи Gradle, smoke output, `deploy/desktop/run/pipeline-status.json`
2. Diagnose — один root cause
3. Fix — минимальный diff
4. Verify — та же команда smoke/test
5. **Не** перескакивать волну при FAIL текущей

---

## Связанные файлы

| Файл | Назначение |
|------|------------|
| `specs/031-desktop-java-client/spec.md` | Требования |
| `specs/031-desktop-java-client/tasks.md` | DC-* чеклист |
| `.cursor/prompts/desktop-client-pipeline.md` | Детали по ролям (одна итерация) |
| `scripts/Start-KorusDesktopPipeline.ps1` | Статус + промпт |

---

## Успех итерации /loop

```
[DESKTOP-DELIVERY] wave=W* role=* artifact=saved verify=PASS
```

Успех **всего цикла**:

```
product_delivery=COMPLETE, buildIntegrity PASS, parity matrix required rows PASS
```
