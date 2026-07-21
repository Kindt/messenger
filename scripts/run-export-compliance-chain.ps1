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
$gitBash = Join-Path ${env:ProgramFiles} "Git\bin\bash.exe"
function Get-SmokeBash {
    if (Test-Path $gitBash) { return $gitBash }
    $cmd = Get-Command bash -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source -notmatch '\\Windows\\System32\\bash\.exe$') { return $cmd.Source }
    throw "Git bash required (avoid WSL store stub)"
}

$steps = @(
    @{ Name = "export-compliance-flow"; Script = "smoke-export-compliance-flow.ps1" }
    @{ Name = "export-compliance-pack"; Script = "smoke-export-compliance-pack.ps1" }
    @{ Name = "openapi-export-compliance"; Script = "smoke-openapi-export-compliance.ps1" }
    @{ Name = "export-gdpr-fulfillment"; Script = "smoke-export-gdpr-fulfillment.ps1" }
    @{ Name = "export-retention-gate"; Script = "smoke-export-retention-gate.ps1" }
)

. (Join-Path $Root "scripts\lib\Resolve-QemuLabWorkerMetrics.ps1")
$metrics = Resolve-QemuLabWorkerMetrics -ApiBaseUrl $ApiBaseUrl
. (Join-Path $Root "scripts\lib\Reset-QemuLabOrgIpAllowlist.ps1")
& (Join-Path $Root "scripts\vpp\Wait-AuthRateLimitCooldown.ps1") -BaseUrl $ApiBaseUrl -MaxSec 180 | Out-Null
Reset-QemuLabOrgIpAllowlist -BaseUrl $ApiBaseUrl | Out-Null

foreach ($s in $steps) {
    Write-Host ""
    Write-Host "=== $($s.Name) ===" -ForegroundColor Cyan
    $path = Join-Path $PSScriptRoot $s.Script
    if (-not (Test-Path $path)) { Write-Host "[FAIL] missing $path"; exit 1 }
    if ($s.Script -like "*.sh") {
        $env:BASE_URL = $ApiBaseUrl
        & (Get-SmokeBash) $path
    } elseif ($s.Script -like "*export-compliance-pack.ps1") {
        & $path -BaseUrl $ApiBaseUrl -WorkerMetricsUrl $metrics.WorkerMetricsUrl -RetentionMetricsUrl $metrics.RetentionMetricsUrl
    } else {
        & $path -BaseUrl $ApiBaseUrl
    }
    if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] $($s.Name)"; exit $LASTEXITCODE }
    Write-Host "[OK] $($s.Name)" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] export compliance chain" -ForegroundColor Green
