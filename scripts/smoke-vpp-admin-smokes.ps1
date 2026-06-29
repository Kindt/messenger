#Requires -Version 5.1
# VPP admin smokes bundle (spec 030).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-vpp-admin-smokes.ps1"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$steps = @(
    "scripts\smoke-retention-purge.ps1",
    "scripts\smoke-admin-export.ps1"
)

foreach ($rel in $steps) {
    Write-Host ""
    Write-Host "=== admin: $rel ===" -ForegroundColor Cyan
    $path = Join-Path $Root $rel
    if (-not (Test-Path $path)) { throw "missing $rel" }
    & $path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "[OK] $rel" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] admin smokes" -ForegroundColor Green
