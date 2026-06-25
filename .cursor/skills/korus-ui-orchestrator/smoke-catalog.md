# Smoke catalog — spec 026 pack verification

Manual protocol: run in **fresh agent chat** (or role-play). Pass = agent behavior matches **Expected** without editing product code unless smoke says so.

Full matrix: `specs/026-cursor-ui-agent-orchestrator/design/classification-matrix.md`

---

## S1 — P1 i18n hotfix

**Prompt:** «Опечатка в ru.json settings — исправь ключ ui.settings.general.title»

| Step | Expected |
|------|----------|
| INTAKE | `[ORCHESTRATOR] pipeline=P1`, Auto-P1, no RD-03 |
| Analyst | `ui-brief` with ≥2 acceptance, parity mention |
| Skip | No ux-spec, no ux-review |
| Engineer | locales + build:locales + parity script in handoff |
| QA | qa-evidence with parity line, `:19088` |

---

## S2 — P2 empty state

**Prompt:** «Добавь empty state для search results»

| Step | Expected |
|------|----------|
| INTAKE | P2 |
| Analyst | brief |
| Designer | full ux-spec with states table |
| UX Evaluator | **ui-ux-review** A/B/C before any `webui/` edit |
| Engineer | Only after ux-review PASS |
| QA | tiers + ux-review ref in evidence |

**Fail if:** code before ux-spec or before ux-review.

---

## S3 — Mobile composer

**Prompt:** «На 375px composer обрезается — поправь»

| Step | Expected |
|------|----------|
| INTAKE | P1 or P2+MOBILE (RD-03 if ambiguous) |
| Modifiers | `+MOBILE`, possibly `+BUNDLE` if JS |
| QA | `ui-mobile` + desktop regression |

---

## S4 — P5 a11y audit

**Prompt:** «Проверь a11y auth form»

| Step | Expected |
|------|----------|
| INTAKE | P5 |
| Role | QA + `accessibility-auditing`; no code unless user approves |

---

## S5 — Settings tab P2

**Prompt:** «Добавь блок scheduled messages в settings notifications»

| Step | Expected |
|------|----------|
| Pipeline | P2 |
| Designer | `settings-tab-notifications`, testids |
| UX Evaluator | axis C = `settings:notifications`, not new tab |
| QA | `ui-auth` tier |

---

## S6 — P1 waiver

**Prompt:** «Без brief срочно — typo в ru.json settings»

| Step | Expected |
|------|----------|
| INTAKE | P1 waiver after user explicit urgent |
| Skip | Analyst/Designer/UX Evaluator |
| QA | `Waiver: user urgent hotfix — skipped analyst/designer` |

---

## S7 — P3 speckit order

**Prompt:** «Закрой UI задачу из specs/016-*»

| Step | Expected |
|------|----------|
| Order | brief → speckit → designer → **UX Evaluator** → engineer → QA |

---

## S8 — Out of scope

**Prompt:** «Сделай landing marketing page»

| Step | Expected |
|------|----------|
| INTAKE | Exit orchestrator — not Korus messenger shell |

---

## S9 — IA / usability audit

**Prompt:** «Reminders в general settings — нормально ли расположено?»

| Step | Expected |
|------|----------|
| INTAKE | P5 or ux-review-only |
| UX Evaluator | ui-ux-review with axis C; likely IA-RED (→ notifications tab) |
| No code | Unless user approves fix list |

---

## Clone simulation (T02643)

After pull, these paths must exist and be trackable:

```
.cursor/skills/korus-ui-orchestrator/
.cursor/skills/korus-ui-role-*/
.cursor/skills/korus-webui/
.cursor/skills/korus-webui-mobile/
.cursor/skills/korus-agent-workflow/
.cursor/rules/korus-ui-team.mdc
```

Verify: `git check-ignore -v .cursor/skills/korus-webui/SKILL.md` → should **not** be ignored.

**Latest smoke evidence:** [`smoke-runs/2026-06-25-S1-S8.md`](smoke-runs/2026-06-25-S1-S8.md) (S1–S8), [`smoke-runs/2026-06-25-S2-S9.md`](smoke-runs/2026-06-25-S2-S9.md) (S2, S9).
