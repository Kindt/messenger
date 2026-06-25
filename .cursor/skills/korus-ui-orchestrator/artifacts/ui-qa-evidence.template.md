# UI QA Evidence

<!-- ARTIFACT:ui-qa-evidence -->

**Change ref:** (brief/ux-spec/task id)  
**Pipeline:**  
**Waiver (P1 only):** none | user urgent hotfix — skipped analyst/designer  
**Author (agent role):** QA  
**Stack:** QEMU UI `http://127.0.0.1:19088/` | other: ___

## Preconditions

- [ ] API health `http://127.0.0.1:18080/api/v1/health` — OK / N/A / BLOCKED
- [ ] Web UI reachable — OK / BLOCKED (reason:)

## Automated tests

| Tier / command | Result | Notes |
|----------------|--------|-------|
| `playwright-dev-loop.ps1 -Tier ___` | PASS / FAIL / SKIP | |
| `ui-mobile` (if mobile) | PASS / FAIL / SKIP | |

## Visual / responsive (Cursor browser or screenshots)

| Viewport | URL/route | OK? | Notes |
|----------|-----------|-----|-------|
| 375 | | | |
| 428 | | | |
| 768 | | | |
| 1280 | | | |

## Console & network

- Console errors: none / list:
- Failed requests: none / list:

## Accessibility (if P5 or required)

- `browser_snapshot` issues: none / list:
- Critical a11y blockers:

## i18n (if strings changed)

- [ ] `node scripts/webui-label-lint.js` — PASS / N/A (`./gradlew checkWebuiLabelLint`)

## UX review (P2/P3 only)

**ux-review ref:**  
**Scores:** A=___ B=___ C=___ D=___ E=___ F=___ G=___  
**IA zone implemented:** yes / no / N/A (P1)

## Verdict

- [ ] **PASS** — ready for done gate (`superpowers-verification-before-completion`)
- [ ] **FAIL** — return to Engineer with findings below

## Findings (if FAIL)

| Severity | Finding | Suggested fix |
|----------|---------|---------------|
| | | |
