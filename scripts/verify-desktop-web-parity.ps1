#Requires -Version 5.1
<#
.SYNOPSIS
  Offline verification: unit tests + web-ui parity audit (no live server).
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    Write-Host '=== Desktop SDK tests + parity audit ===' -ForegroundColor Cyan
    & .\gradlew.bat :modules:desktop-client-sdk:test :modules:desktop-client:compileJava --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host ''
    Write-Host '=== Parity summary (from test WebParityAuditTest) ===' -ForegroundColor Cyan
    Write-Host 'See build report: modules\desktop-client-sdk\build\reports\tests\test\index.html'

    Write-Host ''
    Write-Host '=== Demo UI (no server) ===' -ForegroundColor Cyan
    Write-Host '  $env:KORUS_DESKTOP_DEMO=1'
    Write-Host '  .\gradlew.bat :modules:desktop-client:run'

    Write-Host ''
    Write-Host 'PASS verify-desktop-web-parity (offline)' -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
