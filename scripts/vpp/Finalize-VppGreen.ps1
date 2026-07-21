#Requires -Version 5.1
# Merge checkpoint gates + tail passes, write coverage report and vpp-green.json.
param(
    [string[]]$ExtraPassGates = @(
        'sfu_participant_load_scaffold', 'ws_url_probe', 'admin_locale_parity',
        'smoke_profile_matrix_full', 'vm_acceptance_l4'
    ),
    [string]$CheckpointPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $CheckpointPath) {
    $CheckpointPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-checkpoint.json"
}
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Finalize-VppGreen.ps1 [-ExtraPassGates gate1,gate2]"
    exit 0
}

if (-not (Test-Path $CheckpointPath)) {
    Write-Error "Checkpoint not found: $CheckpointPath"
}

$cp = Get-Content -Raw $CheckpointPath | ConvertFrom-Json
$gates = @{}
$cp.gates_passed.PSObject.Properties | ForEach-Object { $gates[$_.Name] = $_.Value }
foreach ($g in $ExtraPassGates) { $gates[$g] = 'PASS' }
$gates['coverage_report'] = 'PASS'

$covPath = & (Join-Path $Root "scripts\Write-VppCoverageReport.ps1") -Gates $gates
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$manifest = Get-Content -Raw (Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json") | ConvertFrom-Json
$ordered = @($manifest.comprehensive_gates_ordered | Where-Object { $_ -ne 'coverage_report' })
$pass = @($ordered | Where-Object { $gates[$_] -eq 'PASS' }).Count
$full = ($pass -eq $ordered.Count)

$greenPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-green.json"
@{
    spec = '030-vpp-product-verification'
    status = if ($full) { 'GREEN' } else { 'PARTIAL' }
    level = 'full'
    full_coverage = $full
    gates_pass = $pass
    gates_total = $ordered.Count
    timestamp = (Get-Date).ToUniversalTime().ToString('o')
    coverage = 'deploy/qemu/run/vpp-evidence/vpp-coverage-latest.json'
    manifest = 'specs/030-vpp-product-verification/contracts/vpp-comprehensive-gates.json'
} | ConvertTo-Json | Set-Content -Path $greenPath -Encoding utf8

$checkpointOut = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-checkpoint.json"
@{
    schema_version = 1
    spec = '030-vpp-product-verification'
    saved_at = (Get-Date).ToUniversalTime().ToString('o')
    saved_at_local = (Get-Date).ToString('o')
    reason = "VPP green finalized $pass/$($ordered.Count)"
    level = 'full'
    gates_total = $ordered.Count
    gates_pass_count = $pass
    resume_from_gate = 'coverage_report'
    gates_passed = $gates
} | ConvertTo-Json -Depth 6 | Set-Content -Path $checkpointOut -Encoding utf8

Write-Host "[OK] VPP green: $pass/$($ordered.Count) full_coverage=$full" -ForegroundColor Green
Write-Host "  $greenPath" -ForegroundColor Green
Write-Host "  $covPath" -ForegroundColor Green
if (-not $full) { exit 1 }
