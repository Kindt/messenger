# Contract: Fast Full-Stack Acceptance (US9)

## Two loops

| Loop | Script | When |
|------|--------|------|
| Inner | `scripts/playwright-dev-loop.ps1 -Tier <name>` | Every code/test fix while stack up |
| Outer | `scripts/qemu-plan-orchestrator.ps1 -SkipVmUp` | Once before merge / operator sign-off |

## Tier manifest

Source: [tests/e2e-web/playwright-tiers.json](../../../tests/e2e-web/playwright-tiers.json)

| Tier | Scope | Target time |
|------|-------|-------------|
| `api` | profile-settings, e2ee-capabilities, contacts-search (API grep) | &lt; 90s |
| `ui-auth` | auth-session | &lt; 2m |
| `ui-messaging` | messaging-* | &lt; 3m |
| `ui-files` | files-export | &lt; 3m |
| `ui-conference` | conference-rtc | &lt; 3m |
| `ui-e2ee` | e2ee-browser-roundtrip | &lt; 2m |
| `full` | all specs (outer gate only) | &lt; 6m |

## Preflight (before any tier)

- `KORUS_WEB_URL` / `PLAYWRIGHT_BASE_URL` host port **19088**
- `KORUS_API_URL` host port **18080**
- `GET /api/v1/health` OK
- Web shell has login markers (`#u` or `data-testid=auth-submit`)
- `csadmin` login 200; `smoke_user_a` register 200/201/409

Implementation: [deploy/qemu/lib/Invoke-KorusPlanFailureAnalysis.ps1](../../../deploy/qemu/lib/Invoke-KorusPlanFailureAnalysis.ps1) `Test-KorusPlanPlaywrightPreflight`.

## Failure analysis

On tier or outer fail:

1. Write [deploy/qemu/run/plan-failure-analysis.json](../../../deploy/qemu/run/plan-failure-analysis.json)
2. Categories: `web_wrong_port`, `api_csadmin_401`, `preflight_fail`, `test_strict_mode`, `ui_login_timeout`, …
3. `recommendedAction` drives remediation (never blind full-suite retry)

## Block rule

Same `fingerprint` twice with `codeFixRequired` → `blocked` until repo fix or infra remediate.

Outer orchestrator MUST NOT transition `running_playwright` → `running_playwright` on failure.

## Inner tier status

File: `deploy/qemu/run/inner-tier-status.json`

```json
{
  "tiers": {
    "api": { "pass": true, "at": "ISO8601" },
    "ui-messaging": { "pass": false, "at": null, "lastError": "..." }
  },
  "allInnerPass": false
}
```

Outer gate SHOULD run only when `allInnerPass: true` (override: `-SkipInnerTierCheck` on orchestrator).

## i18n

Human summaries: [deploy/qemu/lib/plan-failure-i18n.json](../../../deploy/qemu/lib/plan-failure-i18n.json) (PS scripts stay ASCII-only).
