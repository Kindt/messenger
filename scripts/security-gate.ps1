# Security gate: buildIntegrity + optional QEMU smokes (spec 014).
param(
    [switch]$SkipBuild,
    [switch]$SkipQemuSmokes,
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [double]$MaxTimingDelta = 0.05,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\security-gate.ps1 [-SkipBuild] [-SkipQemuSmokes] [-BaseUrl url]

Runs PR security gate:
  1. ./gradlew buildIntegrity (spotless ratchet, npm audit, benchmark, all tests)
  2. Optional QEMU smokes when API health OK:
     smoke-security-headers, smoke-rate-limit, audit-timing

Examples:
  .\scripts\security-gate.ps1 -SkipQemuSmokes
  .\scripts\security-gate.ps1 -SkipBuild
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
Push-Location $Root
try {
    if (-not $SkipBuild) {
        Write-Host "=== buildIntegrity (spec 014) ===" -ForegroundColor Cyan
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & .\gradlew.bat buildIntegrity --no-daemon 2>&1 | Out-Host
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($buildExit -ne 0) {
            Write-Host "[FAIL] buildIntegrity" -ForegroundColor Red
            exit $buildExit
        }
        Write-Host "[OK] buildIntegrity" -ForegroundColor Green
    }

    if ($SkipQemuSmokes) {
        Write-Host "[OK] security-gate (build only)" -ForegroundColor Green
        exit 0
    }

    $healthOk = $false
    try {
        $null = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
        $healthOk = $true
    } catch {
        Write-Host "[SKIP] QEMU smokes: API not reachable at $BaseUrl (use -SkipQemuSmokes for build-only CI parity)" -ForegroundColor Yellow
        exit 0
    }

    if (-not $healthOk) { exit 0 }

    Write-Host "=== QEMU security smokes ===" -ForegroundColor Cyan
    & "$Root\scripts\smoke-security-headers.ps1" -BaseUrl $BaseUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$Root\scripts\smoke-rate-limit.ps1" -BaseUrl $BaseUrl
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$Root\scripts\audit-timing.ps1" -BaseUrl $BaseUrl -MaxDeltaRatio $MaxTimingDelta
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "[OK] security-gate (build + QEMU smokes)" -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
