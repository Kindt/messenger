#Requires -Version 5.1
param(
    [string] $BaseUrl = $(if ($env:KORUS_LIVE_API_URL) { $env:KORUS_LIVE_API_URL } else { 'http://127.0.0.1:18080' }),
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\smoke-desktop-health.ps1" -BaseUrl $BaseUrl | Out-Null

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

$caps = Invoke-RestMethod -Uri "$BaseUrl/api/v1/platform/capabilities" -Headers $h
if (-not $caps) { Write-Error 'capabilities empty' }

Write-Host 'PASS smoke-desktop-capabilities (authenticated)'
exit 0
