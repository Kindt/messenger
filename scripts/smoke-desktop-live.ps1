#Requires -Version 5.1
<#
.SYNOPSIS
  Full desktop live-server smoke against QEMU :18080.
#>
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    $env:KORUS_LIVE_API_URL = $BaseUrl
    $env:KORUS_DESKTOP_SMOKE_USER = $Username
    $env:KORUS_DESKTOP_SMOKE_PASSWORD = $Password

    Write-Host "=== Desktop live smokes ($BaseUrl) ===" -ForegroundColor Cyan
    & "$PSScriptRoot\smoke-desktop-health.ps1" -BaseUrl $BaseUrl
    & "$PSScriptRoot\smoke-desktop-auth.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password -SkipUi
    & "$PSScriptRoot\smoke-desktop-messaging.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-branding.ps1" -Live -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-search.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-capabilities.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-files.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-websocket.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-read-receipts.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password
    & "$PSScriptRoot\smoke-desktop-calls.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password

    Write-Host "=== Gradle liveServerTest ===" -ForegroundColor Cyan
    & .\gradlew.bat :modules:desktop-client-sdk:liveServerTest --no-configuration-cache -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host ""
    Write-Host "PASS smoke-desktop-live (full server integration)" -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
