# Spec 014 T01431: optional live-backend verification (requires creds in integrations/.env on guest).
param(
    [string]$ApiBase = "http://127.0.0.1:18080/api",
    [string]$IntegrationsGateway = "http://127.0.0.1:18190",
    [switch]$SkipPreflight
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $root "deploy\qemu\run\ssh-hostkeys.ps1")

if (-not $SkipPreflight) {
    & (Join-Path $PSScriptRoot "integrations-gate-preflight.ps1") -Online | Out-Null
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$hostKey = $script:KorusQemuSshHostKeys['integrations']
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
if (-not (Test-Path $plink)) { throw "plink not found" }

$mode = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12223 -Script @"
grep -E '^INTEGRATIONS_BACKEND_MODE=' /mnt/korus/integrations/.env 2>/dev/null || echo INTEGRATIONS_BACKEND_MODE=mock
"@

if ($mode -notmatch 'live') {
    Write-Host "[SKIP] INTEGRATIONS_BACKEND_MODE is not live on guest (.env missing or mock)" -ForegroundColor Yellow
    Write-Host "  Set integrations/.env on guest per specs/014-bot-plugin-platform/contracts/integrations-live-gate.md" -ForegroundColor DarkGray
    Write-Host "  Mock gate: .\scripts\smoke-integrations-gate.ps1" -ForegroundColor DarkGray
    exit 0
}

Write-Host "=== Live integrations gate (T01431) ===" -ForegroundColor Cyan
& (Join-Path $PSScriptRoot "smoke-integrations-gate.ps1") -SkipPreflight
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "[OK] Live integrations gate (mock smokes on live-mode stack)" -ForegroundColor Green
Write-Host "Spot-check real backends manually per integrations-live-gate.md" -ForegroundColor DarkGray
