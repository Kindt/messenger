# Desktop Java Client — одна фаза конвейера (spec 031)

**Скопируй всё ниже** или получи актуальный блок:

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt
```

Для **полной поставки W0→W4** используй [`desktop-client-full-delivery.md`](desktop-client-full-delivery.md).

---

<!-- PIPELINE-INJECT:START -->
<!-- Заменяется скриптом: wave, pipeline, role, plan_review, matrix_rows, wave_guide -->
<!-- PIPELINE-INJECT:END -->

---

## Preconditions (все роли)

**Режим:** непрерывный конвейер (`continuous-conveyor.md`) — роли подряд, без паузы после каждой. Hard gates только для Engineer.

1. `.cursor/skills/korus-desktop-orchestrator/SKILL.md`
2. `deploy/desktop/run/pipeline-status.json` — текущие `wave`, `role`, `pipeline`, `plan_review`
3. Wave guide: `.cursor/skills/korus-desktop-orchestrator/waves/{W0|W1|W2|W3|W4}.md`
4. Spec: `specs/031-desktop-java-client/spec.md`

**Engineer / QaVerifier дополнительно:**

```powershell
# Stack (integration)
Invoke-WebRequest -Uri http://127.0.0.1:18080/api/v1/health -UseBasicParsing
# Если не 200 → .cursor/prompts/qemu-stack-cycle.md, затем вернуться
```

---

## Роль: Analyst

**Skill:** `korus-desktop-role-analyst`

### Задача

Сформировать brief для **текущей волны** по wave guide и matrix rows.

### Шаги

1. Прочитай wave guide и отфильтрованные matrix rows (из inject блока)
2. Заполни `.cursor/skills/korus-desktop-orchestrator/artifacts/desktop-brief.template.md`
3. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-brief.md`
4. Выведи в чат краткую версию с `<!-- ARTIFACT:desktop-brief -->`

### DoD

- [ ] Problem 2–4 предложения
- [ ] ≥2 acceptance Given/When/Then
- [ ] Out of scope ≥1 пункт
- [ ] Matrix row ids перечислены
- [ ] Multi-server / multi-profile impact (или N/A для W0)

### Verify

Файл существует на диске, gate checklist brief (spec design/agent-pipeline.md).

### Далее

→ Architect (без паузы)

---

## Роль: Architect

**Skill:** `korus-desktop-role-architect`  
**Input:** `desktop-brief.md` текущей волны

### Задача

План реализации: модули, классы, task IDs, smokes, риски.

### Шаги

1. Прочитай brief + wave guide (полный blueprint классов/экранов)
2. Заполни `desktop-plan.template.md`
3. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-plan.md`
4. `<!-- ARTIFACT:desktop-plan -->` в чат

### DoD

- [ ] Task IDs DC-* из tasks.md
- [ ] Module touch list
- [ ] Test plan с точными gradle/smoke командами
- [ ] ADR stub если E2EE/WebRTC в scope

### Далее

→ Designer

---

## Роль: Designer

**Skill:** `korus-desktop-role-designer`  
**Subagent:** `.cursor/agents/korus-desktop-designer.md`  
**Input:** `desktop-brief.md` + `desktop-plan.md`  
**Framework:** `specs/031-desktop-java-client/design/desktop-ux-framework.md`

### Задача

Проработать IA и UX **до кода**: что на экране, что в настройках/«Серверы», состояния, `DesktopUiIds`.

### Шаги

1. Прочитай brief, plan, framework, текущий `MainShellView` / `SettingsView`
2. Заполни `desktop-ux-spec.template.md`
3. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-ux-spec.md`
4. `<!-- ARTIFACT:desktop-ux-spec -->` в чат

### DoD

- [ ] Таблица on screen vs settings/servers для каждой фичи волны
- [ ] Navigation graph + states per surface
- [ ] Карта `DesktopUiIds` (существующие + новые)
- [ ] Master–detail для вкладки «Чаты»
- [ ] Нет «набора кнопок» без обоснования IA

### Далее

→ UX Evaluator

---

## Роль: UxEvaluator

**Skill:** `korus-desktop-role-ux-evaluator`  
**Subagent:** `.cursor/agents/korus-desktop-ux-evaluator.md`  
**Input:** `desktop-ux-spec.md`

### Задача

Независимая оценка IA/usability (оси A–G). Verdict: **PASS** | **FAIL** | **N/A** (`+SDK_ONLY`).

### Шаги

1. Оцени по `desktop-ux-framework.md`
2. Заполни `desktop-ux-review.template.md`
3. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-ux-review.md`
4. Обнови статус:

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview PASS -Advance
# или FAIL -Rollback / N/A -Advance
```

### DoD

- [ ] Оси A–F ≥3 (G N/A допустим) или явный waiver пользователя
- [ ] Brief acceptance сверен с ux-spec
- [ ] RED flags (IA/ICON/LABEL/COMP) закрыты или waived

### Далее

FAIL → Designer; PASS → `-SetUxReview PASS -Advance`

---

## Роль: PlanReviewer

**Skill:** `korus-desktop-role-plan-reviewer`  
**Input:** brief + plan + **desktop-ux-spec** + **desktop-ux-review (PASS|N/A)**

### Задача

Независимое ревью. Verdict: **APPROVED** или **CHANGES_REQUESTED**.

### Checklist (все пункты)

- [ ] `ux_review` == PASS или N/A
- [ ] Scope = brief, без scope creep в webui/core-api
- [ ] ContactRef `(serverId, userId)` если W1+
- [ ] Profile isolation / secrets
- [ ] Attachments path если W2+
- [ ] QEMU smokes, не host Docker
- [ ] Matrix rows покрыты
- [ ] E2EE defer documented если не в scope

### Шаги

1. Заполни `desktop-plan-review.template.md`
2. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-plan-review.md`
3. При **APPROVED**:

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved
```

При **CHANGES_REQUESTED**:

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview ChangesRequested -Rollback
```

### Далее

CHANGES_REQUESTED → Architect; APPROVED → `-SetPlanReview Approved -Advance` → Engineer

---

## Роль: Engineer

**Skill:** `korus-desktop-role-engineer`

### Preconditions (hard)

```
plan_review == APPROVED   (D2/D3)
ux_review == PASS or N/A  (D2/D3; N/A only +SDK_ONLY with reason in ux-review)
```

Иначе **вернись** к UxEvaluator / PlanReviewer (fix, не пауза).

### Задача

Реализуй plan **и** approved `desktop-ux-spec` (ids, IA, states).

### Шаги

1. Создай/измени файлы по `desktop-plan.md` и `waves/W*.md`
2. TDD для SDK: тесты **до** или вместе с реализацией
3. Обнови `settings.gradle.kts` / `libs.versions.toml` если в плане
4. Прогони:

```powershell
.\gradlew.bat :modules:desktop-client-sdk:test
.\gradlew.bat :modules:desktop-client:compileJava
.\gradlew.bat buildIntegrity
```

5. Отметь DC-* в `tasks.md` для выполненных пунктов

### DoD

- [ ] Все пункты plan реализованы
- [ ] SDK tests PASS
- [ ] buildIntegrity PASS
- [ ] Нет JavaFX в `desktop-client-sdk`

### Далее

→ QA_VERIFIER (сразу после verify)

---

## Роль: QaVerifier

**Skill:** `korus-desktop-role-qa-verifier`

### Задача

Приёмка волны: matrix rows, smokes, evidence.

### Команды (по волне)

| Wave | Commands |
|------|----------|
| W0 | `smoke-desktop-health.ps1`, `smoke-desktop-auth.ps1 -SkipUi` |
| W1 | `+ smoke-desktop-profiles.ps1`, `smoke-desktop-multi-server.ps1` |
| W2 | `+ smoke-desktop-messaging.ps1` |
| W3 | `+ smoke-desktop-capabilities.ps1`, `smoke-desktop-search.ps1`, `smoke-desktop-calls.ps1` |
| W4 | `+ smoke-desktop-update-manifest.ps1`, `smoke-desktop-full-parity.ps1` |

Всегда:

```powershell
.\gradlew.bat buildIntegrity
.\gradlew.bat :modules:desktop-client-sdk:test
```

### Шаги

1. Заполни `desktop-qa-evidence.template.md` со **свежим** выводом
2. **Сохрани:** `specs/031-desktop-java-client/artifacts/waves/{wave}/desktop-qa-evidence.md`
3. `<!-- ARTIFACT:desktop-qa-evidence -->` + verdict PASS/FAIL

### При PASS

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -Advance
# Если QaVerifier → Done для волны:
.\scripts\Start-KorusDesktopPipeline.ps1 -CompleteWave
```

### DoD

- [ ] Каждая matrix row волны: PASS / DEFER + ADR
- [ ] buildIntegrity PASS
- [ ] Evidence file на диске

### После PASS

`-Advance` + `-CompleteWave` → сразу Analyst следующей волны.

---

## Pipeline variants

| ID | Sequence | Когда |
|----|----------|-------|
| **D2** | A → Arch → **Designer** → **UxEval** → PR → Eng → QA | default, новые волны / UI |
| **D1** | A → Eng → QA | SDK hotfix; UX waiver в qa-evidence |
| **D3** | A → Arch → Designer → UxEval → PR | только spec/plan |
| **D5** | QA | parity audit |

`-Advance` учитывает `pipeline` из status JSON.

---

## Hard rules

- ENGINEER blocked if `plan_review != APPROVED` (D2/D3)
- ENGINEER blocked if `ux_review` not in `PASS`,`N/A` (D2/D3)
- UI changes require approved `desktop-ux-spec`
- QEMU `:18080` for integration; no host Docker
- Commits only on user request
- Cloud agents forbidden

---

## Артефакты

| Artifact | Path |
|----------|------|
| Status | `deploy/desktop/run/pipeline-status.json` |
| Latest prompt | `deploy/desktop/run/pipeline-latest-prompt.txt` |
| Wave artifacts | `specs/031-desktop-java-client/artifacts/waves/W*/` |
| UX framework | `specs/031-desktop-java-client/design/desktop-ux-framework.md` |
| UX spec/review templates | `.cursor/skills/korus-desktop-orchestrator/artifacts/desktop-ux-*.template.md` |
