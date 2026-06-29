# Spec 027: wait for quiescent QEMU stack, sync webui, enable demo skins, run Playwright ui-branding.
param(
    [int]$WaitTimeoutMinutes = 45,
    [switch]$SkipWebSync,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\run-ui-branding-qemu.ps1 [-WaitTimeoutMinutes 45] [-SkipWebSync]

Waits until no redeploy locks / guest core-api-rebuild, repairs web lb if needed,
syncs webui (no full redeploy), enables demo_skins via admin API, runs ui-branding tier.

Safe alongside other agents: does not call qemu-redeploy or qemu-sync-api-core.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"

$env:KORUS_WEB_URL = "http://127.0.0.1:19088"
$env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
$env:KORUS_API_URL = "http://127.0.0.1:18080"

. (Join-Path $Root "deploy\qemu\lib\Wait-KorusPlanPlaywrightStack.ps1")

Write-Host "=== ui-branding QEMU run $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
$null = Wait-KorusPlanPlaywrightStack -Root $Root -RunDir $RunDir `
    -TimeoutMinutes $WaitTimeoutMinutes -IntervalSec 60 -BusyIntervalSec 20

if (-not $SkipWebSync) {
    & (Join-Path $Root "scripts\qemu-web-sync.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$apiUrl = $env:KORUS_API_URL
try {
    $loginBody = '{"username":"csadmin","password":"csadmin"}'
    $login = Invoke-RestMethod -Uri "$apiUrl/api/v1/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
    $headers = @{ Authorization = "Bearer $($login.access_token)" }
    $pub = Invoke-RestMethod -Uri "$apiUrl/api/v1/branding" -TimeoutSec 10
    if (-not $pub.demo_skins_enabled) {
        Write-Host "Enabling demo_skins_enabled on platform branding..." -ForegroundColor Cyan
        $putBody = '{"palette":"korus","demo_skins_enabled":true}'
        $null = Invoke-RestMethod -Uri "$apiUrl/api/v1/admin/branding/platform" -Method PUT `
            -Headers $headers -ContentType "application/json" -Body $putBody
    }
    try {
        $null = Invoke-RestMethod -Uri "$apiUrl/api/v1/branding/manifest.webmanifest" -TimeoutSec 10
    } catch {
        Write-Warning 'manifest.webmanifest not ready - run: .\scripts\qemu-sync-api-core.ps1 -NoCache ; .\scripts\qemu-guest-job.ps1 -Loop'
        exit 2
    }
} catch {
    Write-Warning "demo skins / branding setup skipped: $($_.Exception.Message)"
}

& (Join-Path $Root "scripts\playwright-dev-loop.ps1") -Tier ui-branding -SkipPreflight -SyncWebUi $false
exit $LASTEXITCODE
