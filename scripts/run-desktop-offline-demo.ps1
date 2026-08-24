#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$env:KORUS_DESKTOP_DEMO = '1'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    & .\scripts\verify-desktop-web-parity.ps1
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'Launching desktop demo UI...' -ForegroundColor Cyan
    & .\gradlew.bat :modules:desktop-client:run -Dkorus.desktop.demo=true --no-configuration-cache
}
finally {
    Pop-Location
}
