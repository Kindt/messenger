# Multi-user messaging E2E smoke (spec 003). Windows wrapper.
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$WsUrl = "ws://127.0.0.1:8082/ws",
    [switch]$SkipEnsureUsers
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$sh = Join-Path $scriptDir "smoke-messaging-e2e.sh"
if (-not (Test-Path $sh)) { Write-Error "Not found: $sh" }

$env:BASE_URL = $BaseUrl
$env:WS_URL = $WsUrl
$args = @()
if ($SkipEnsureUsers) { $args += "--skip-ensure-users" }

& bash $sh @args
exit $LASTEXITCODE
