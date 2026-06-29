#Requires -Version 5.1
# Short strict WS soak for VPP extended gate (no guest redeploy).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [int]$WsConnections = 8,
    [int]$WsDurationSeconds = 45,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppWsSoakLight.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
& (Join-Path $Root "scripts\load-ws-soak-qemu.ps1") `
    -ApiBaseUrl $ApiBaseUrl `
    -SkipServerRedeploy `
    -SkipUpload `
    -SkipFanout `
    -WsConnections $WsConnections `
    -WsDurationSeconds $WsDurationSeconds `
    -Strict
exit $LASTEXITCODE
