#Requires -Version 5.1
# Write VPP failure analysis for diagnose-fix-retry loop (spec 030).
param(
    [int]$Attempt = 1,
    [int]$MaxAttempts = 5,
    [string]$Level = "standard",
    [hashtable]$Gates = @{},
    [hashtable]$Dimensions = @{},
    [string]$LastFailedGate = "",
    [int]$LastExitCode = 1,
    [string]$OutPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\Write-VppFailureAnalysis.ps1 -Attempt 1 -Gates @{ x = 'FAIL' } -LastFailedGate buildIntegrity"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$EvDir = Join-Path $RunDir "vpp-evidence"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

$remediationPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-failure-remediation.json"
$remediation = $null
if (Test-Path $remediationPath) {
    $remediation = Get-Content -Raw $remediationPath | ConvertFrom-Json
}

$failedGates = @($Gates.GetEnumerator() | Where-Object { $_.Value -eq "FAIL" } | ForEach-Object { $_.Key })
if ($LastFailedGate -and $failedGates -notcontains $LastFailedGate) {
    $failedGates = @($LastFailedGate) + $failedGates
}

$hints = @()
foreach ($g in $failedGates) {
    $entry = $remediation.gates.$g
    if ($entry) {
        $hints += @{
            gate = $g
            log_paths = @($entry.log_paths)
            fix_hints = @($entry.fix_hints)
        }
    } else {
        $hints += @{ gate = $g; log_paths = @(); fix_hints = @("See scripts/SMOKE_INDEX.md and specs/030 quickstart") }
    }
}

if (-not $OutPath) {
    $OutPath = Join-Path $EvDir ("vpp-failure-analysis-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")
}

$doc = [ordered]@{
    spec = "030-vpp-product-verification"
    title = "VPP failure analysis"
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    attempt = $Attempt
    max_attempts = $MaxAttempts
    level = $Level
    last_exit_code = $LastExitCode
    last_failed_gate = $LastFailedGate
    failed_gates = $failedGates
    dimensions = $Dimensions
    gates = $Gates
    remediation = $hints
    mandatory_next_steps = @(
        "1. Read full logs (not last line only) from remediation.log_paths",
        "2. Diagnose root cause",
        "3. Apply minimal fix in repo or guest stack",
        "4. Restart affected step (rebuild/sync/smoke)",
        "5. Re-run ENTIRE VPP complex until all gates PASS",
        "6. Do not declare product OK until VPP -UntilGreen succeeds"
    )
    log_artifacts = @(
        "deploy/qemu/run/plan-failure-analysis.json",
        "deploy/qemu/run/inner-tier-status.json",
        "deploy/qemu/run/status-minute.log",
        "build/reports/"
    )
}

$doc | ConvertTo-Json -Depth 10 | Set-Content -Path $OutPath -Encoding utf8

$summaryPath = Join-Path $RunDir "vpp-failure-analysis.json"
Copy-Item -Path $OutPath -Destination $summaryPath -Force
$latestPath = Join-Path $EvDir "vpp-failure-analysis-latest.json"
Copy-Item -Path $OutPath -Destination $latestPath -Force

Write-Host ""
Write-Host "========================================" -ForegroundColor Red
Write-Host "  VPP FAILED (attempt $Attempt/$MaxAttempts)" -ForegroundColor Red
Write-Host "  Gate: $LastFailedGate (exit $LastExitCode)" -ForegroundColor Red
Write-Host "  Analysis: $OutPath" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Red
Write-Host ""
Write-Host "Mandatory loop: analyze -> fix -> retry -> full VPP until GREEN" -ForegroundColor Cyan
foreach ($h in $hints) {
    Write-Host ""
    Write-Host "  [$($h.gate)]" -ForegroundColor Yellow
    foreach ($f in $h.fix_hints) { Write-Host "    - $f" }
}

Write-Output $OutPath
