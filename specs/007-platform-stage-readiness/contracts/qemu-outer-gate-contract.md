# Contract: QEMU outer gate (007)

**Applies to:** Windows dev host + QEMU guests

## Engineering acceptance (007)

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | Redeploy lock does not block auto-remediate when process dead | `Korus-QemuRedeployLock.ps1` PID probe |
| 2 | SSH host key cache refreshes on mismatch | `Test-KorusPlinkHostKeyValid` in `Get-KorusEd25519HostKey` |
| 3 | wsUrl mismatch triggers **Force** WebOnly redeploy | `Start-KorusQemuGuestRedeploy -Force`; plan orchestrator `redeploy_web` |
| 4 | Host wsUrl probe script exists | `scripts/test-korus-wsurl.ps1` exit 0 when stack correct |
| 5 | Playwright counts synced | `ops-signoff-log.md` + `runtime-gate-report.md` = 30/30 |

## Operator gate (T701 — pending re-run)

```powershell
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp
```

**Pass:** smoke green → Playwright 30/30 **without** manual `ssh-hostkeys.ps1` edit or `docker compose --force-recreate` on host.

**Fail remediate:** check `deploy/qemu/run/status-remediate.log`; one auto `redeploy_web -Force` cycle should suffice.
