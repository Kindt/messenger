#Requires -Version 5.1
# Full mobile product verification (spec 032) - all waves + buildIntegrity
param([switch]$SkipBuildIntegrity, [switch]$Help)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$WaveScript = Join-Path $Root "scripts\smoke-mobile-wave.ps1"

if ($Help) {
    Write-Host @"
Usage: .\scripts\Run-KorusMobileProductVerify.ps1

Runs smoke-mobile-wave W0..W4 then buildIntegrity.
Requires QEMU :18080 for API smokes (W0+).
"@
    exit 0
}

foreach ($w in @('W0','W1','W2','W3','W4')) {
    Write-Host ""
    Write-Host "========== Wave $w ==========" -ForegroundColor Magenta
    & $WaveScript -Wave $w
    if ($LASTEXITCODE -ne 0) {
        Write-Host ('[FAIL] wave ' + $w) -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

if (-not $SkipBuildIntegrity) {
    Write-Host ""
    Write-Host "========== buildIntegrity ==========" -ForegroundColor Magenta
    & (Join-Path $Root "gradlew.bat") buildIntegrity --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host ""
Write-Host ('[PASS] Run-KorusMobileProductVerify - mobile product gates OK') -ForegroundColor Green
exit 0
