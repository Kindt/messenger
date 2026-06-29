#Requires -Version 5.1
# Admin UI locale key parity (VPP fortress).
param(
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppAdminLocaleParity.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script = Join-Path $Root "scripts\admin-ui-locale-parity-audit.js"
if (-not (Test-Path $script)) {
    Write-Host "[FAIL] missing $script" -ForegroundColor Red
    exit 1
}
Push-Location $Root
try {
    node $script
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
