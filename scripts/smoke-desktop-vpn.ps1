#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& .\gradlew.bat :modules:desktop-client-sdk:test --tests "*VpnProfileTest" --no-configuration-cache -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host '[PASS] smoke-desktop-vpn (all protocols registry + 2FA stub)' -ForegroundColor Green
