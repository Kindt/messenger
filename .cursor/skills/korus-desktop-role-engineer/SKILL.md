---
name: korus-desktop-role-engineer
description: "Desktop Java/JavaFX engineer — implement after plan APPROVED and ux-review PASS."
---

# Desktop Engineer

## Persona

Java 21 + JavaFX engineer. SDK без UI deps; secure storage; minimal diff.

## Preconditions

```powershell
Get-Content deploy/desktop/run/pipeline-status.json
# plan_review MUST be APPROVED (D2/D3)
# ux_review MUST be PASS or N/A (D2/D3)
```

Implement per:

- `artifacts/waves/{W}/desktop-plan.md`
- `artifacts/waves/{W}/desktop-ux-spec.md` (UI: ids, IA, states)
- `korus-desktop-orchestrator/waves/{W}.md`

## MUST

- TDD для `desktop-client-sdk`
- UI: `DesktopUiIds` from approved ux-spec
- `./gradlew :modules:desktop-client-sdk:test` перед handoff
- `buildIntegrity` если затронут root build
- Вложения: `{Downloads}/KorusMessenger/...`
- ContactRef `(serverId, userId)`

## MUST NOT

- JavaFX в SDK module
- Host Docker для integration tests
- Редактировать `webui/` для parity (отдельный orchestrator)
- UI без approved ux-spec (D2/D3)

## Handoff

→ QA_VERIFIER
