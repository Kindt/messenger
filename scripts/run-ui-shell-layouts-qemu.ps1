# Spec 028: wait for quiescent QEMU stack, sync webui, reset platform layout probe, run Playwright ui-shell-layouts.
param(
    [int]$WaitTimeoutMinutes = 45,
    [switch]$SkipWebSync,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\run-ui-shell-layouts-qemu.ps1 [-WaitTimeoutMinutes 45] [-SkipWebSync]

Waits until no redeploy locks / guest core-api-rebuild, repairs web lb if needed,
syncs webui, ensures API branding exposes shell_layout (V076), runs ui-shell-layouts tier.

Safe alongside other agents: does not call qemu-redeploy or qemu-sync-api-core.
If V076 missing on guest, run: .\scripts\qemu-sync-api-core.ps1 -NoCache ; .\scripts\qemu-guest-job.ps1 -Loop
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"

$env:KORUS_WEB_URL = "http://127.0.0.1:19088"
$env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
$env:KORUS_API_URL = "http://127.0.0.1:18080"

. (Join-Path $Root "deploy\qemu\lib\Wait-KorusPlanPlaywrightStack.ps1")

Write-Host "=== ui-shell-layouts QEMU run $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
$null = Wait-KorusPlanPlaywrightStack -Root $Root -RunDir $RunDir `
    -TimeoutMinutes $WaitTimeoutMinutes -IntervalSec 60 -BusyIntervalSec 20

if (-not $SkipWebSync) {
    & (Join-Path $Root "scripts\qemu-web-sync.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$apiUrl = $env:KORUS_API_URL
try {
    $pub = Invoke-RestMethod -Uri "$apiUrl/api/v1/branding" -TimeoutSec 10
    if ($null -eq $pub.shell_layout) {
        Write-Warning 'shell_layout missing on public branding - guest needs V076 + core-api rebuild'
        Write-Host "Run: .\scripts\qemu-sync-api-core.ps1 -NoCache ; .\scripts\qemu-guest-job.ps1 -Loop" -ForegroundColor Yellow
        exit 2
    }
    Write-Host "Public branding shell_layout=$($pub.shell_layout) auth=$($pub.auth_layout)" -ForegroundColor DarkGray
} catch {
    Write-Warning "Branding probe failed: $($_.Exception.Message)"
    exit 2
}

& (Join-Path $Root "scripts\playwright-dev-loop.ps1") -Tier ui-shell-layouts -SkipPreflight -SyncWebUi $false
exit $LASTEXITCODE
