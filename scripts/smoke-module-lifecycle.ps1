#Requires -Version 5.1
# VPP-2 orchestrator: programmatic + physical module/plugin lifecycle (spec 030).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$ProgrammaticOnly,
    [switch]$PhysicalOnly,
    [switch]$SkipPlugins,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-module-lifecycle.ps1 [-ProgrammaticOnly] [-PhysicalOnly] [-SkipPlugins]

Runs:
  1. smoke-module-lifecycle-programmatic.ps1  (admin override, capabilities)
  2. smoke-module-lifecycle-physical.ps1        (docker workers, KORUS_PRODUCT_ADDONS)
  3. smoke-plugin-lifecycle.ps1                (integrations VM plugin bridges)

Matrix: specs/030-vpp-product-verification/contracts/plugin-lifecycle-matrix.json
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot

function Step([string]$Name, [string]$ScriptRel, [switch]$NoBaseUrl) {
    Write-Host ""
    Write-Host "=== lifecycle: $Name ===" -ForegroundColor Cyan
    $path = Join-Path $Root ($ScriptRel -replace '/', '\')
    if (-not (Test-Path $path)) { throw "missing $ScriptRel" }
    if ($NoBaseUrl) { & $path } else { & $path -BaseUrl $BaseUrl }
    $stepExit = if ($null -ne $LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    if ($stepExit -ne 0) {
        Write-Host "[FAIL] lifecycle step '$Name' (exit $stepExit)" -ForegroundColor Red
        exit $stepExit
    }
}

function Wait-IntegrationsForPlugins {
    Write-Host ""
    Write-Host "=== lifecycle: wait integrations before plugins ===" -ForegroundColor Cyan
    $waitApi = Join-Path $Root "deploy\qemu\run\wait-api-health.ps1"
    if (Test-Path $waitApi) {
        & $waitApi -MaxMinutes 10
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & (Join-Path $Root "scripts\vpp\Wait-IntegrationsOnline.ps1") -MaxSec 900 -StartVmIfDown -RepairGateway
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if ($PhysicalOnly) {
    Step "physical modules" "scripts/smoke-module-lifecycle-physical.ps1"
    if (-not $SkipPlugins) { Step "physical plugins" "scripts/smoke-plugin-lifecycle.ps1" -NoBaseUrl }
    Write-Host "[OK] module lifecycle (physical only)" -ForegroundColor Green
    exit 0
}

if ($ProgrammaticOnly) {
    Step "programmatic modules" "scripts/smoke-module-lifecycle-programmatic.ps1"
    Write-Host "[OK] module lifecycle (programmatic only)" -ForegroundColor Green
    exit 0
}

Step "programmatic modules" "scripts/smoke-module-lifecycle-programmatic.ps1"
Step "physical modules" "scripts/smoke-module-lifecycle-physical.ps1"
if (-not $SkipPlugins) {
    Wait-IntegrationsForPlugins
    Step "plugin platform" "scripts/smoke-plugin-lifecycle.ps1" -NoBaseUrl
}

Write-Host ""
Write-Host "[OK] module lifecycle (programmatic + physical + plugins)" -ForegroundColor Green
