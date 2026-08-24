#Requires -Version 5.1
# Run all mobile smokes for wave W0–W4 (spec 032 matrix)
param(
    [ValidateSet('W0', 'W1', 'W2', 'W3', 'W4')]
    [string]$Wave = 'W0',
    [string]$ApiBase = "http://127.0.0.1:18080",
    [switch]$SkipSdkTests,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$MatrixPath = Join-Path $Root "specs\032-mobile-native-client\contracts\feature-parity-matrix.json"
$Lib = Join-Path $PSScriptRoot "lib\SmokeMobile.ps1"

if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-mobile-wave.ps1 -Wave W0

Runs SDK tests + API smokes mapped in feature-parity-matrix.json for the wave.
Requires QEMU API :18080 for API smokes (W0 auth+).
"@
    exit 0
}

. $Lib

if (-not (Test-Path $MatrixPath)) {
    Invoke-KorusMobileFail "matrix missing: $MatrixPath"
}

$matrix = Get-Content $MatrixPath -Raw | ConvertFrom-Json
$rows = @($matrix.rows | Where-Object { $_.wave -eq $Wave })

# Prime token cache once per wave to avoid auth rate limits
. $Lib
if (Test-KorusMobileHealth -BaseUrl $ApiBase) {
    $null = Get-KorusMobileToken -BaseUrl $ApiBase
}

Write-Host "== Mobile wave $Wave smokes ($($rows.Count) matrix rows) ==" -ForegroundColor Cyan

if (-not $SkipSdkTests) {
    if ($Wave -in @('W0', 'W1', 'W2', 'W3', 'W4')) {
        Invoke-KorusMobileSdkTests
    }
}

$needApi = $rows | Where-Object { $_.mobile_smoke -match 'smoke-mobile-(auth|messaging|files|contacts|capabilities|push|calls|search|e2ee)' }
if ($needApi -and -not (Test-KorusMobileHealth -BaseUrl $ApiBase)) {
    Invoke-KorusMobileFail "API unhealthy at $ApiBase - run qemu-stack-cycle first"
}

$failed = @()
foreach ($row in $rows) {
    if ($row.status -eq 'deferred') {
        Write-Host ('[SKIP] ' + $row.id + ' deferred') -ForegroundColor Yellow
        continue
    }
    $scriptName = $row.mobile_smoke
    $scriptPath = Join-Path $PSScriptRoot $scriptName
    Write-Host ""
    Write-Host "-- $($row.id) ($scriptName) --" -ForegroundColor Cyan
    if (-not (Test-Path $scriptPath)) {
        Write-Host "[SKIP] script not found: $scriptName" -ForegroundColor Yellow
        continue
    }
    try {
        & $scriptPath -ApiBase $ApiBase
        if ($LASTEXITCODE -ne 0) { $failed += $row.id }
    } catch {
        if ($row.status -eq 'optional') {
            Write-Host ('[SKIP optional] ' + $row.id + ': ' + $_.Exception.Message) -ForegroundColor Yellow
        } else {
            $failed += $row.id
            Write-Host ('[FAIL] ' + $row.id + ': ' + $_.Exception.Message) -ForegroundColor Red
        }
    }
}

if ($failed.Count -gt 0) {
    Invoke-KorusMobileFail "failed rows: $($failed -join ', ')"
}

Write-Host ""
Write-Host ('[PASS] smoke-mobile-wave ' + $Wave) -ForegroundColor Green
exit 0
