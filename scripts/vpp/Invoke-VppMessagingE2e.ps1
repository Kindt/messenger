#Requires -Version 5.1
# Single messaging E2E pass on QEMU forwards.
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WsUrl = "ws://127.0.0.1:18082/ws",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) { Write-Host "Usage: Invoke-VppMessagingE2e.ps1"; exit 0 }
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bash = Get-Command bash -ErrorAction SilentlyContinue
if (-not $bash) { Write-Host "[FAIL] bash required"; exit 1 }
$env:BASE_URL = $ApiBaseUrl
& bash (Join-Path $Root "scripts/smoke-messaging-e2e.sh") --url $ApiBaseUrl --ws-url $WsUrl --skip-ensure-users
exit $LASTEXITCODE
