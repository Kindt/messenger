#Requires -Version 5.1
# Incremental VPP gate progress for live monitoring (spec 030).
param(
    [string]$Level = "full",
    [int]$Attempt = 1,
    [string]$GateKey = "",
    [string]$Status = "",
    [hashtable]$Gates = @{},
    [string]$LastFailedGate = "",
    [int]$LastExitCode = 0,
    [string]$CurrentGate = "",
    [string]$Phase = "running",
    [switch]$Init,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) { Write-Host "Usage: Update-VppLiveProgress.ps1 -Init | -GateKey x -Status PASS"; exit 0 }

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
$OutPath = Join-Path $EvDir "vpp-live-progress.json"

$manifestPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json"
$totalGates = 145
if (Test-Path $manifestPath) {
    $m = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $totalGates = @($m.comprehensive_gates_ordered | Where-Object { $_ -ne "coverage_report" }).Count
}

$prev = @{}
if (Test-Path $OutPath) {
    try {
        $rawPrev = Get-Content -Raw $OutPath | ConvertFrom-Json
        if ($rawPrev.session_start) { $prev.session_start = $rawPrev.session_start }
        if ($rawPrev.phase) { $prev.phase = $rawPrev.phase }
        if ($rawPrev.current_gate) { $prev.current_gate = $rawPrev.current_gate }
        if ($rawPrev.last_failed_gate) { $prev.last_failed_gate = $rawPrev.last_failed_gate }
        if ($rawPrev.last_exit_code) { $prev.last_exit_code = $rawPrev.last_exit_code }
        if ($rawPrev.gates) {
            $prev.gates = @{}
            foreach ($p in $rawPrev.gates.PSObject.Properties) { $prev.gates[$p.Name] = $p.Value }
        }
    } catch { }
}

$gateMap = @{}
if ($Gates -and $Gates.Count -gt 0) {
    foreach ($k in $Gates.Keys) { $gateMap[$k] = $Gates[$k] }
} elseif ($prev.gates) {
    foreach ($k in $prev.gates.Keys) { $gateMap[$k] = $prev.gates[$k] }
}
if ($GateKey -and $Status) { $gateMap[$GateKey] = $Status }

$passed = @($gateMap.Values | Where-Object { $_ -eq "PASS" }).Count
$failed = @($gateMap.Values | Where-Object { $_ -eq "FAIL" }).Count
$skipped = @($gateMap.Values | Where-Object { $_ -eq "SKIP" }).Count

$doc = [ordered]@{
    spec = "030-vpp-product-verification"
    updated_at = (Get-Date).ToUniversalTime().ToString("o")
    level = $Level
    attempt = $Attempt
    phase = if ($Phase) { $Phase } elseif ($prev.phase) { $prev.phase } else { "running" }
    gates_total = $totalGates
    gates_pass = $passed
    gates_fail = $failed
    gates_skip = $skipped
    gates_remaining = [math]::Max(0, $totalGates - $passed - $failed - $skipped)
    current_gate = if ($CurrentGate) { $CurrentGate } elseif ($GateKey) { $GateKey } else { $prev.current_gate }
    last_failed_gate = if ($LastFailedGate) { $LastFailedGate } else { $prev.last_failed_gate }
    last_exit_code = if ($LastExitCode -ne 0) { $LastExitCode } else { $prev.last_exit_code }
    gates = $gateMap
}
if ($Init -or -not $prev.session_start) {
    $doc.session_start = (Get-Date).ToUniversalTime().ToString("o")
} elseif ($prev.session_start) {
    $doc.session_start = $prev.session_start
}

$doc | ConvertTo-Json -Depth 6 | Set-Content -Path $OutPath -Encoding utf8
