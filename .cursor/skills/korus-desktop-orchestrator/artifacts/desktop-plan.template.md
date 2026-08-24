# Desktop implementation plan

<!-- ARTIFACT:desktop-plan -->

**Wave:** W_  
**Brief ref:** (link or summary)  
**Author role:** ARCHITECT  
**Date:**  

## Module touch list

| Module | Change |
|--------|--------|
| `desktop-client-sdk` | |
| `desktop-client` | |
| `build.gradle.kts` / `settings.gradle.kts` | |

## Task IDs (DC-*)

- DC-___

## Design decisions

### Storage / secrets



### WS / API per server



### UI surfaces (JavaFX)



## ADRs / deferrals

| Topic | Decision | Wave |
|-------|----------|------|

## Test plan

| Check | Command / script |
|-------|------------------|
| SDK unit | `./gradlew :modules:desktop-client-sdk:test` |
| buildIntegrity | `./gradlew buildIntegrity` |
| Smoke | `scripts/smoke-desktop-*.ps1` |

## Risks

| Risk | Mitigation |

## Handoff to Plan Reviewer

- [ ] Scope bounded
- [ ] Matrix rows listed
- [ ] No host Docker runtime
