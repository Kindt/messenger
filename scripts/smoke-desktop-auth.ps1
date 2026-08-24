#Requires -Version 5.1
<#
.SYNOPSIS
  Smoke: auth API against QEMU (optional UI skip).
#>
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' }),
    [switch] $SkipUi
)

$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\smoke-desktop-health.ps1" -BaseUrl $BaseUrl | Out-Null

$capUri = "$BaseUrl/api/v1/platform/capabilities"
Write-Host "GET $capUri"
$cap = Invoke-WebRequest -Uri $capUri -UseBasicParsing -TimeoutSec 15
if ($cap.StatusCode -ne 200) { Write-Error "Capabilities failed: $($cap.StatusCode)" }
Write-Host "PASS capabilities ($($cap.StatusCode))"

if ($Username -and $Password) {
    $loginUri = "$BaseUrl/api/v1/auth/login"
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json
    Write-Host "POST $loginUri (user=$Username)"
    $r = Invoke-WebRequest -Uri $loginUri -Method POST -Body $body -ContentType 'application/json' -UseBasicParsing
    if ($r.StatusCode -ne 200) { Write-Error "Login failed: $($r.StatusCode)" }
    Write-Host "PASS login ($($r.StatusCode))"
}
else {
    Write-Host "SKIP login (set KORUS_DESKTOP_SMOKE_USER / KORUS_DESKTOP_SMOKE_PASSWORD for full auth smoke)"
}

if (-not $SkipUi) {
    Write-Host "NOTE: UI login smoke requires running desktop-client; use -SkipUi in CI until jpackage exists"
}

Write-Host "PASS smoke-desktop-auth"
exit 0
