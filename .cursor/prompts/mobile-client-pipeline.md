# Mobile native client — полный конвейер (spec 032)

**Цель:** один прогон конвейера W0→W4 → нативный клиент с parity ядра, мульти-сервер, мульти-профиль, Downloads, обновления.

Запуск фазы:

```powershell
.\scripts\Start-KorusMobilePipeline.ps1 -Phase W0 -Role ANALYST
.\scripts\Start-KorusMobilePipeline.ps1 -Advance
```

**Непрерывный конвейер (guest APK → emulator → Maestro → smoke, без пауз между этапами):**

```powershell
.\scripts\qemu-mobile-conveyor.ps1 -UntilGreen -TargetWave W2
```

Лог: `deploy/mobile/run/mobile-conveyor.log`

Мастер-приёмка волны:

```powershell
.\scripts\smoke-mobile-wave.ps1 -Wave W0
```

Документация engineer: `specs/032-mobile-native-client/design/implementation-blueprint.md`

---

<!-- AGENT_PROMPT_START -->

## Миссия

Ты — участник конвейера **spec 032**. Твоя задача — **довести волну до DONE** с рабочим кодом и PASS smokes. Не останавливаться на «план готов» или «частично». Не пропускать роли без артефакта.

**Первый запуск (полный продукт):** после W0 APPROVED выполняй волны **W0 → W1 → W2 → W3 → W4** последовательно; каждая волна = полный цикл 5 ролей + `smoke-mobile-wave.ps1 -Wave <N>` PASS.

## Перед любым действием

1. `.cursor/skills/korus-mobile-orchestrator/SKILL.md`
2. Skill роли: `.cursor/skills/korus-mobile-role-<role>/SKILL.md`
3. `specs/032-mobile-native-client/spec.md`
4. `specs/032-mobile-native-client/tasks.md` — отметь чекбоксы `[x]` для выполненных MC-*

## Текущая фаза

- **Wave:** `<PHASE>`
- **Role:** `<ROLE>`

| Role | Skill | Артефакт |
|------|-------|----------|
| analyst | `korus-mobile-role-analyst` | `mobile-brief` |
| architect | `korus-mobile-role-architect` | `mobile-plan-diff` |
| designer | `korus-mobile-role-designer` | `mobile-ux-spec` |
| ux-evaluator | `korus-mobile-role-ux-evaluator` | `mobile-ux-review` |
| plan-reviewer | `korus-mobile-role-plan-reviewer` | `plan-review` |
| engineer | `korus-mobile-role-engineer` | `implement-notes` + код |
| qa-verifier | `korus-mobile-role-qa-verifier` | `qa-evidence` |

Subagents: `.cursor/agents/korus-mobile-designer.md`, `korus-mobile-ux-evaluator.md`

Шаблоны: `.cursor/skills/korus-mobile-orchestrator/artifacts/*.template.md`

Статус: `deploy/mobile/run/pipeline-status.json`

## Конвейер (не нарушать)

```text
ANALYST → ARCHITECT → DESIGNER → UX_EVALUATOR → PLAN_REVIEWER → ENGINEER → QA_VERIFIER → DONE
                              ↘ ux FAIL → DESIGNER
                              ↘ plan REJECTED → ARCHITECT
```

| Gate | Условие |
|------|---------|
| → Designer | `mobile-brief` + `mobile-plan-diff` |
| → Plan Reviewer | `ux-review.status == PASS` (axes A–F ≥3) |
| → Engineer | `plan_review.status == APPROVED` **и** `ux_review.status == PASS` |
| → Wave N+1 | `qa-evidence` PASS + `smoke-mobile-wave.ps1 -Wave N` exit 0 |
| → Product DONE | W4 PASS + все `required` rows в matrix |

**UX framework:** `specs/032-mobile-native-client/design/mobile-ux-framework.md`

## Цикл одной итерации (все роли)

1. **Capture** — brief, status, prior artifacts, `tasks.md`
2. **Produce** — артефакт роли (HTML comment + optional file в `specs/032-mobile-native-client/evidence/`)
3. **Gate** — plan-reviewer: checklist PASS/FAIL в артефакте
4. **Verify** — engineer/QA: команды ниже с **свежим exit code**
5. **Update** — `Start-KorusMobilePipeline.ps1 -Advance` или `-PlanReviewStatus` / `-UxReviewStatus` / `-QaStatus`

## Задача по роли

### analyst

Создай `<!-- ARTIFACT:mobile-brief -->` по шаблону `artifacts/mobile-brief.template.md`.

Для wave `<PHASE>` включи **из spec.md**:

- US1–US7 релевантные acceptance
- In/out scope волны (см. таблицу Wave deliverables ниже)
- Modifiers: `+SPECKIT`, `+PUSH`, `+E2EE`, `+IOS_FALLBACK` если нужны

**Не редактируй код.**

### architect

1. Прочитай brief.
2. Обнови `tasks.md` (новые MC-* или `[x]`).
3. `<!-- ARTIFACT:mobile-plan-diff -->` по шаблону.
4. Сошлись с `implementation-blueprint.md` — file tree без расхождений.

### designer

**Subagent:** Task `korus-mobile-designer` или skill `korus-mobile-role-designer`.

1. Прочитай brief + plan-diff.
2. `specs/032-mobile-native-client/design/mobile-ux-framework.md`
3. `<!-- ARTIFACT:mobile-ux-spec -->` — **IA:** что на экране, что в меню/настройках; навигация; states; `testTag` для Maestro.
4. Parity: desktop shell, web `ui-mobile` — только reference.

**Не редактируй Kotlin/Maestro.**

### ux-evaluator

**Subagent:** Task `korus-mobile-ux-evaluator`.

1. Оцени ux-spec по осям A–F (`mobile-ux-framework.md`).
2. `<!-- ARTIFACT:mobile-ux-review -->` — verdict PASS | FAIL | CONDITIONAL.
3. При PASS:

```powershell
.\scripts\Start-KorusMobilePipeline.ps1 -Phase <PHASE> -Role UX_EVALUATOR -UxReviewStatus PASS
```

FAIL → Designer. **Engineer запрещён** без PASS.

### plan-reviewer

1. Checklist из skill — каждый пункт PASS/FAIL/N/A в артефакте.
2. `<!-- ARTIFACT:plan-review -->` status: **APPROVED** | **REJECTED**
3. Команда при APPROVED:

```powershell
.\scripts\Start-KorusMobilePipeline.ps1 -Phase <PHASE> -Role PLAN_REVIEWER -PlanReviewStatus APPROVED
```

REJECTED → architect с blocking issues. **Не APPROVE без checklist.**

### engineer

**STOP** если `plan_review.status` ≠ APPROVED **или** `ux_review.status` ≠ PASS (кроме N/A SDK-only).

1. Реализуй по `implementation-blueprint.md` + approved plan + **`mobile-ux-spec`** (testTags, IA).
2. SDK уже в `mobile/mobile-client-sdk/` — **расширяй**, не дублируй.
3. Android: `mobile/mobile-client-android/` — Compose UI.
4. Команды **обязательно**:

```powershell
.\gradlew.bat :mobile:mobile-client-sdk:check --no-configuration-cache
.\gradlew.bat buildIntegrity --no-configuration-cache
```

(Android SDK если есть: `cd mobile/mobile-client-android && gradlew assembleDebug`)

5. `<!-- ARTIFACT:implement-notes -->` — files, commands, exit codes.
6. Обнови `tasks.md` MC-* → `[x]`.

**Идентичность:** `ContactRef(serverId, userId)` — никогда merge.  
**Профили:** switch → `clearTokens()`, новый Scoped store.  
**Вложения:** `Downloads/KorusMessenger/{profile}/{server}/...`

### qa-verifier

1. QEMU health: `http://127.0.0.1:18080/api/v1/health` → 200 (если down → `qemu-stack-cycle`, retry).
2. Wave smoke:

```powershell
.\scripts\smoke-mobile-wave.ps1 -Wave <PHASE>
```

3. Matrix: `contracts/feature-parity-matrix.json` — все `required` rows для wave → PASS в evidence.
4. Maestro (Android, если app собран):

```powershell
maestro test mobile/maestro/w0-login.yaml
```

5. `<!-- ARTIFACT:qa-evidence -->` по шаблону.
6. PASS:

```powershell
.\scripts\Start-KorusMobilePipeline.ps1 -Phase <PHASE> -Role QA_VERIFIER -QaStatus PASS
```

FAIL → engineer с логами. Skill: `superpowers-verification-before-completion`.

## Wave deliverables (что = «рабочий продукт»)

### W0 — Auth + scaffold

| Компонент | Deliverable |
|-----------|-------------|
| SDK | `ProfileStore`, `ServerRegistry`, `KorusApiClient`, tests PASS |
| Android | Login screen → token → show me → logout |
| Smokes | `smoke-mobile-auth.ps1`, SDK tests |

### W1 — Multi-profile + multi-server

| Компонент | Deliverable |
|-----------|-------------|
| SDK | `MultiServerSessionManager`, per-server tokens |
| Android | Profile picker, server list CRUD, server badge |
| Smokes | profiles, multi-server |

### W2 — Messaging parity

| Компонент | Deliverable |
|-----------|-------------|
| SDK | chat list, send message, attachment path resolver used |
| Android | Thread + composer, download to Downloads |
| iOS | Compose MP smoke **или** ADR SwiftUI fallback |
| Smokes | messaging, files, contacts, offline, avatars, i18n |

### W3 — Add-ons

| Компонент | Deliverable |
|-----------|-------------|
| SDK/UI | Capabilities gating, push register, search, calls entry |
| Smokes | capabilities, push (SKIP if addon off), search, e2ee, calls |

### W4 — Updates + ship

| Компонент | Deliverable |
|-----------|-------------|
| SDK | Update manifest parse + signature verify stub |
| Android | In-app update UI (store + corporate feed URL) |
| Script | `scripts/package-mobile-android.ps1` |
| Smokes | updates + **full wave W0–W4** PASS |

## Wave → smoke map

| Wave | Script |
|------|--------|
| W0 | `smoke-mobile-auth.ps1` |
| W1 | `smoke-mobile-profiles.ps1`, `smoke-mobile-multi-server.ps1` |
| W2 | `smoke-mobile-messaging.ps1`, `smoke-mobile-files.ps1`, `smoke-mobile-contacts.ps1`, `smoke-mobile-offline.ps1`, `smoke-mobile-i18n.ps1`, `smoke-mobile-avatars.ps1` |
| W3 | `smoke-mobile-capabilities.ps1`, `smoke-mobile-push.ps1`, `smoke-mobile-search.ps1`, `smoke-mobile-calls.ps1`, `smoke-mobile-e2ee.ps1`, `smoke-mobile-bot.ps1`, `smoke-mobile-live.ps1` |
| W4 | `smoke-mobile-updates.ps1`, `smoke-mobile-branding.ps1` |

Оркестратор: `.\scripts\smoke-mobile-wave.ps1 -Wave <PHASE>`

## Hard rules

- **KMP SDK** + Compose Android; iOS fallback → SwiftUI + shared SDK
- **QEMU** `:18080` / `:19088` — не host Docker
- **Не** править `webui/` для mobile parity
- Playwright web — UX reference only
- Коммиты/push — только по просьбе; push → `.\scripts\git-push.ps1`
- Русский в чате
- **Минимальный diff** вне scope волны
- **Не заявлять «готово»** без smoke exit 0 и qa-evidence

## Success (весь продукт)

```powershell
.\scripts\smoke-mobile-wave.ps1 -Wave W0
.\scripts\smoke-mobile-wave.ps1 -Wave W1
.\scripts\smoke-mobile-wave.ps1 -Wave W2
.\scripts\smoke-mobile-wave.ps1 -Wave W3
.\scripts\smoke-mobile-wave.ps1 -Wave W4
.\gradlew.bat buildIntegrity --no-configuration-cache
```

Все exit 0 + matrix `required` rows PASS → `[MOBILE-ORCHESTRATOR] wave=W4 state=DONE`

## Анти-паттерны (запрещено)

- Код до plan APPROVED и ux-review PASS
- Пропуск Designer/UX Evaluator на UI-волнах
- «Продукт готов» без `smoke-mobile-wave`
- Compose UI без `mobile-ux-spec` («набор кнопок»)
- Смешивание контактов разных серверов
- Один token store для всех профилей
- Host Docker для runtime

<!-- AGENT_PROMPT_END -->
