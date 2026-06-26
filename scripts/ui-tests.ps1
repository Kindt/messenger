# С‚РµСЃС‚С‹ UI вЂ” data-driven Playwright scenarios (QEMU :19088 / :18080).
param(
    [ValidateSet("smoke", "pr", "full")]
    [string]$Profile = "smoke",
    [switch]$SkipPreflight,
    [switch]$SyncWebUi,
    [int]$WaitTimeoutMinutes = 90,
    [int]$WaitIntervalSec = 180,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
=== С‚РµСЃС‚С‹ UI ===

Usage: .\scripts\ui-tests.ps1 [-Profile smoke|pr|full] [-SyncWebUi] [-SkipPreflight]

Waits for QEMU VM/stack before run (default). Per-test wait in Playwright if stack drops mid-suite.
Profiles: smoke ~138 | pr ~400+ | full 1400 scenarios.
-SkipPreflight: skip host wait + health gate (debug only).
-WaitTimeoutMinutes / -WaitIntervalSec: host poll before first test (default ping every 3 min).

Profiles:
  smoke  ~138 scenarios (PR gate)
  pr     smoke + pr-tier scenarios (~400+)
  full   entire manifest (1400)

Requires QEMU stack: http://127.0.0.1:19088 / http://127.0.0.1:18080

Examples:
  .\scripts\ui-tests.ps1
  .\scripts\ui-tests.ps1 -Profile smoke -SyncWebUi
  .\scripts\playwright-dev-loop.ps1 -Tier ui-tests
"@
    exit 0
}

Write-Host ""
Write-Host "=== С‚РµСЃС‚С‹ UI ===" -ForegroundColor Cyan
Write-Host "profile=$Profile" -ForegroundColor DarkGray
Write-Host "live: deploy\qemu\run\ui-tests-live.json  (watch: .\scripts\ui-tests-watch.ps1)" -ForegroundColor DarkGray
Write-Host ""

$Root = Split-Path -Parent $PSScriptRoot
$runLog = Join-Path $Root "deploy\qemu\run\ui-tests-run.log"
$runDir = Split-Path $runLog
if (-not (Test-Path $runDir)) { New-Item -ItemType Directory -Path $runDir -Force | Out-Null }
"=== ui-tests started $(Get-Date -Format o) profile=$Profile ===" | Set-Content -Path $runLog -Encoding UTF8

$env:UI_TESTS_PROFILE = $Profile

$loopScript = Join-Path $PSScriptRoot "playwright-dev-loop.ps1"
$waitArgs = @{
    WaitTimeoutMinutes = $WaitTimeoutMinutes
    WaitIntervalSec    = $WaitIntervalSec
}
if ($SyncWebUi -and $SkipPreflight) {
    & $loopScript -Tier ui-tests -SyncWebUi -SkipPreflight @waitArgs
} elseif ($SyncWebUi) {
    & $loopScript -Tier ui-tests -SyncWebUi @waitArgs
} elseif ($SkipPreflight) {
    & $loopScript -Tier ui-tests -SkipPreflight @waitArgs
} else {
    & $loopScript -Tier ui-tests @waitArgs
}
exit $LASTEXITCODE
