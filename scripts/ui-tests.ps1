# тесты UI — data-driven Playwright scenarios (QEMU :19088 / :18080).
param(
    [ValidateSet("smoke", "pr", "full")]
    [string]$Profile = "smoke",
    [switch]$SkipPreflight,
    [switch]$SyncWebUi,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
=== тесты UI ===

Usage: .\scripts\ui-tests.ps1 [-Profile smoke|pr|full] [-SyncWebUi] [-SkipPreflight]

Profiles:
  smoke  ~120 scenarios (PR gate)
  pr     smoke + pr-tier scenarios
  full   entire manifest

Requires QEMU stack: http://127.0.0.1:19088 / http://127.0.0.1:18080

Examples:
  .\scripts\ui-tests.ps1
  .\scripts\ui-tests.ps1 -Profile smoke -SyncWebUi
  .\scripts\playwright-dev-loop.ps1 -Tier ui-tests
"@
    exit 0
}

Write-Host ""
Write-Host "=== тесты UI ===" -ForegroundColor Cyan
Write-Host "profile=$Profile" -ForegroundColor DarkGray
Write-Host ""

$env:UI_TESTS_PROFILE = $Profile

$loopScript = Join-Path $PSScriptRoot "playwright-dev-loop.ps1"
if ($SyncWebUi -and $SkipPreflight) {
    & $loopScript -Tier ui-tests -SyncWebUi -SkipPreflight
} elseif ($SyncWebUi) {
    & $loopScript -Tier ui-tests -SyncWebUi
} elseif ($SkipPreflight) {
    & $loopScript -Tier ui-tests -SkipPreflight
} else {
    & $loopScript -Tier ui-tests
}
exit $LASTEXITCODE
