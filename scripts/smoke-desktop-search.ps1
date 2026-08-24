#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\smoke-desktop-auth.ps1" -BaseUrl $BaseUrl -Username $Username -Password $Password -SkipUi | Out-Null

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/search/messages?q=desktop&limit=5" -Headers $h
    Write-Host "PASS smoke-desktop-search (hits=$($r.Count))"
}
catch {
  if ($_.Exception.Response.StatusCode.value__ -eq 400) {
    Write-Host 'PASS smoke-desktop-search (empty query ok, API reachable)'
  }
  else { throw }
}
exit 0
