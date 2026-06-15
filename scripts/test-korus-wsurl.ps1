# Host-side wsUrl probe for QEMU web stack (W2-B1.1).
param(
    [string]$RunDir = "",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if (-not $RunDir) {
    $RunDir = Join-Path $Root "deploy\qemu\run"
}
. (Join-Path $Root "deploy\qemu\lib\Get-KorusLanHostIp.ps1")

$expected = Read-KorusQemuLanHostIp -RunDir $RunDir
if (Test-KorusWebClientWsHostMismatch -RunDir $RunDir -WebBaseUrl $WebBaseUrl) {
    if (-not $Quiet) {
        Write-Host "[FAIL] web-client-env.js wsUrl missing expected LAN host $expected" -ForegroundColor Red
    }
    exit 1
}
if (-not $Quiet) {
    Write-Host "[OK] wsUrl contains $expected" -ForegroundColor Green
}
exit 0
