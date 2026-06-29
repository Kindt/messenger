#Requires -Version 5.1
# Messaging E2E with load rounds on QEMU forwards (VPP fortress).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WsUrl = "ws://127.0.0.1:18082/ws",
    [int]$LoadRounds = 5,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppMessagingLoadRounds.ps1 [-LoadRounds 5]"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bash = Get-Command bash -ErrorAction SilentlyContinue
if (-not $bash) {
    Write-Host "[FAIL] bash required" -ForegroundColor Red
    exit 1
}

$env:BASE_URL = $ApiBaseUrl
$sh = Join-Path $Root "scripts\smoke-messaging-e2e.sh"
& bash $sh --url $ApiBaseUrl --ws-url $WsUrl --skip-ensure-users --load-rounds $LoadRounds
exit $LASTEXITCODE
