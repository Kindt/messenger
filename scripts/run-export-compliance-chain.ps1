#Requires -Version 5.1
# Minimal export compliance chain (spec 029 W6 / L4).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\run-export-compliance-chain.ps1"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$steps = @(
    @{ Name = "export-compliance-flow"; Script = "smoke-export-compliance-flow.sh" }
    @{ Name = "export-compliance-pack"; Script = "smoke-export-compliance-pack.sh" }
    @{ Name = "openapi-export-compliance"; Script = "smoke-openapi-export-compliance.sh" }
    @{ Name = "export-gdpr-fulfillment"; Script = "smoke-export-gdpr-fulfillment.ps1" }
    @{ Name = "export-retention-gate"; Script = "smoke-export-retention-gate.ps1" }
)

foreach ($s in $steps) {
    Write-Host ""
    Write-Host "=== $($s.Name) ===" -ForegroundColor Cyan
    $path = Join-Path $PSScriptRoot $s.Script
    if (-not (Test-Path $path)) { Write-Host "[FAIL] missing $path"; exit 1 }
    if ($s.Script -like "*.sh") {
        $bash = Get-Command bash -ErrorAction SilentlyContinue
        if (-not $bash) { Write-Host "[FAIL] bash required for $($s.Script)"; exit 1 }
        $env:BASE_URL = $ApiBaseUrl
        & bash $path
    } else {
        & $path -BaseUrl $ApiBaseUrl
    }
    if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] $($s.Name)"; exit $LASTEXITCODE }
    Write-Host "[OK] $($s.Name)" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] export compliance chain" -ForegroundColor Green
