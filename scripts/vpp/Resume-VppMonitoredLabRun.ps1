#Requires -Version 5.1
# Continue VPP from vpp-checkpoint.json (skip gates already PASS).
param(
    [string]$CheckpointPath = "",
    [int]$MaxAttempts = 10,
    [int]$TickSec = 60,
    [switch]$SkipStackPrep,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not $CheckpointPath) { $CheckpointPath = Join-Path $EvDir 'vpp-checkpoint.json' }

if ($Help) {
    Write-Host @'
Usage: .\scripts\vpp\Resume-VppMonitoredLabRun.ps1 [-SkipStackPrep]

Requires deploy/qemu/run/vpp-evidence/vpp-checkpoint.json (Save-VppCheckpoint.ps1).
Skips gates marked PASS; continues from resume_from_gate.
'@
    exit 0
}

if (-not (Test-Path $CheckpointPath)) {
    Write-Error "Checkpoint not found: $CheckpointPath. Run Save-VppCheckpoint.ps1 first."
}

$cp = Get-Content -Raw $CheckpointPath | ConvertFrom-Json
$env:VPP_RESUME_CHECKPOINT = $CheckpointPath
$level = if ($cp.level) { $cp.level } else { 'full' }

Write-Host "=== VPP RESUME from checkpoint ===" -ForegroundColor Cyan
Write-Host "  $($cp.gates_pass_count)/$($cp.gates_total) PASS already; next gate: $($cp.resume_from_gate)" -ForegroundColor Cyan
Write-Host "  Saved: $($cp.saved_at_local) ($($cp.reason))" -ForegroundColor DarkGray

& (Join-Path $Root 'scripts\vpp\Start-VppMonitoredLabRun.ps1') -Level $level -MaxAttempts $MaxAttempts `
    -TickSec $TickSec -SkipStackPrep:$SkipStackPrep -NoStop -Resume
exit $LASTEXITCODE
