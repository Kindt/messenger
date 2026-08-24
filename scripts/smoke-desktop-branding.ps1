#Requires -Version 5.1
param(
    [switch] $Live,
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

if ($Live) {
    $pub = Invoke-RestMethod -Uri "$BaseUrl/api/v1/branding" -TimeoutSec 10
    if (-not $pub.palette) { Write-Error 'public branding missing palette' }
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
        -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
    $me = Invoke-RestMethod -Uri "$BaseUrl/api/v1/branding/me" -Headers @{ Authorization = "Bearer $($login.access_token)" }
    Write-Host "PASS smoke-desktop-branding live palette=$($pub.palette) me=$($me.palette)"
    exit 0
}

& .\gradlew.bat :modules:desktop-client-sdk:test --tests "*BrandingPaletteTest" --no-configuration-cache -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host '[PASS] smoke-desktop-branding (offline palette + API model)' -ForegroundColor Green
