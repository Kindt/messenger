# Plan: Spec 014 — Code Security + Presentation

**Spec:** [`spec.md`](spec.md)  
**Design source:** [`docs/plans/2026-06-16-code-security-presentation-plan.md`](../../docs/plans/2026-06-16-code-security-presentation-plan.md)

---

## Technical approach

### Phase S1 — CI gate (this sprint)

1. **Gradle `buildIntegrity`** extends with:
   - `spotlessCheck` + `ratchetFrom("origin/main")` — только diff vs main
   - `:modules:core-api:benchmark`
   - `checkNpmAudit` (`npm audit --audit-level=high`)
2. **CI** — один шаг `buildIntegrity`; убрать `continue-on-error` на benchmark
3. **`scripts/security-gate.ps1`** — buildIntegrity + optional QEMU smokes
4. **JVM test args** — `-XX:+EnableDynamicAgentLoading` для JDK 25

### Phase S2 — Depth (follow-up)

- Extended audit-timing endpoints
- Bot webhook HMAC
- CSP prod default
- `docs/SECURITY.md` matrix

### Phase PRES — Presentation (parallel S1)

- `product_status.py` v2.5.4
- `PRODUCT_PRESENTATION.md` §24
- `registry.json` methodology footnote
- Rebuild HTML scripts

---

## Files touched (S1)

| File | Change |
|------|--------|
| `build.gradle.kts` | ratchet, buildIntegrity deps, checkNpmAudit |
| `.github/workflows/ci.yml` | npm audit via buildIntegrity |
| `scripts/security-gate.ps1` | new orchestrator |
| `scripts/SMOKE_INDEX.md` | security-gate entry |
| `docs/CI_AND_REPO_HYGIENE.md` | gate docs |

---

## Verification

```powershell
./gradlew buildIntegrity
.\scripts\security-gate.ps1 -SkipQemuSmokes   # build only
.\scripts\security-gate.ps1                   # full if QEMU up
```
