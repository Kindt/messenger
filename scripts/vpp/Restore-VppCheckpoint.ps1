#Requires -Version 5.1
# Restore vpp-live-progress + checkpoint from saved checkpoint (undo false PASS gates).
param(
    [string]$CheckpointPath = "",
    [string[]]$RemoveGatePass = @(),
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not $CheckpointPath) { $CheckpointPath = Join-Path $EvDir 'vpp-checkpoint.json' }

if ($Help) {
    Write-Host 'Usage: Restore-VppCheckpoint.ps1 [-RemoveGatePass gate1,gate2]'
    exit 0
}

if (-not (Test-Path $CheckpointPath)) { throw "Missing $CheckpointPath" }

$cp = Get-Content -Raw $CheckpointPath | ConvertFrom-Json
$passed = @{}
foreach ($p in $cp.gates_passed.PSObject.Properties) {
    if ($p.Value -eq 'PASS' -and ($RemoveGatePass -notcontains $p.Name)) {
        $passed[$p.Name] = 'PASS'
    }
}
foreach ($g in $RemoveGatePass) {
    if ($passed.ContainsKey($g)) { $passed.Remove($g) }
}

$resumeGate = [string]$cp.resume_from_gate
if ($RemoveGatePass -contains $resumeGate) { $resumeGate = [string]$cp.resume_from_gate }

$doc = [ordered]@{
    schema_version = 1
    spec = '030-vpp-product-verification'
    saved_at = (Get-Date).ToUniversalTime().ToString('o')
    saved_at_local = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')
    reason = 'restored from checkpoint after false PASS repair'
    level = if ($cp.level) { $cp.level } else { 'full' }
    attempt = if ($cp.attempt) { [int]$cp.attempt } else { 1 }
    session_start = if ($cp.session_start) { $cp.session_start } else { (Get-Date).ToUniversalTime().ToString('o') }
    gates_total = if ($cp.gates_total) { [int]$cp.gates_total } else { 145 }
    gates_pass_count = $passed.Count
    resume_from_gate = $resumeGate
    gates_passed = $passed
    playwright_partial = if ($resumeGate -match '^ui_ux_') { $cp.playwright_partial } else { $null }
    resume_command = '.\scripts\vpp\Resume-VppMonitoredLabRun.ps1 -SkipStackPrep'
}

$out = Join-Path $EvDir 'vpp-checkpoint.json'
$doc | ConvertTo-Json -Depth 8 | Set-Content -Path $out -Encoding utf8

$update = Join-Path $PSScriptRoot 'Update-VppLiveProgress.ps1'
if (Test-Path $update) {
    & $update -Level $doc.level -Attempt $doc.attempt -Gates $passed -CurrentGate $resumeGate -Phase 'paused'
}

Write-Host "[restore] $($passed.Count)/$($doc.gates_total) PASS; resume=$resumeGate" -ForegroundColor Green
