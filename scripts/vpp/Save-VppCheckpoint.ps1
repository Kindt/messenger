#Requires -Version 5.1
# Save VPP lab progress for resume (spec 030). Does not stop processes - call Stop-VppLabRun separately.
param(
    [string]$Reason = "manual pause",
    [string]$ResumeGate = "",
    [string]$OutPath = "",
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not $OutPath) { $OutPath = Join-Path $EvDir 'vpp-checkpoint.json' }

if ($Help) {
    Write-Host @'
Usage: .\scripts\vpp\Save-VppCheckpoint.ps1 [-Reason "user pause"]

Writes deploy/qemu/run/vpp-evidence/vpp-checkpoint.json from vpp-live-progress.json.
Resume: .\scripts\vpp\Resume-VppMonitoredLabRun.ps1
'@
    exit 0
}

$livePath = Join-Path $EvDir 'vpp-live-progress.json'
if (-not (Test-Path $livePath)) {
    Write-Error "No vpp-live-progress.json - nothing to checkpoint."
}

$live = Get-Content -Raw $livePath | ConvertFrom-Json
$manifestPath = Join-Path $Root 'specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json'
$ordered = @()
if (Test-Path $manifestPath) {
    $m = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $ordered = @($m.comprehensive_gates_ordered | Where-Object { $_ -ne 'coverage_report' })
}

$passed = @{}
if ($live.gates) {
    foreach ($p in $live.gates.PSObject.Properties) {
        if ($p.Value -eq 'PASS') { $passed[$p.Name] = 'PASS' }
    }
}

$resumeGate = if ($ResumeGate) { $ResumeGate } else { [string]$live.current_gate }
if (-not $resumeGate -and $live.last_failed_gate) { $resumeGate = [string]$live.last_failed_gate }
if (-not $resumeGate -and $ordered.Count) {
    foreach ($g in $ordered) {
        if (-not $passed.ContainsKey($g)) { $resumeGate = $g; break }
    }
}

$pw = $null
$pwScript = Join-Path $PSScriptRoot 'Get-VppPlaywrightProgress.ps1'
if (Test-Path $pwScript) {
    try { $pw = & $pwScript } catch { }
}

$nowLocal = Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'
$doc = [ordered]@{
    schema_version = 1
    spec = '030-vpp-product-verification'
    saved_at = (Get-Date).ToUniversalTime().ToString('o')
    saved_at_local = $nowLocal
    reason = $Reason
    level = if ($live.level) { $live.level } else { 'full' }
    attempt = if ($live.attempt) { [int]$live.attempt } else { 1 }
    session_start = if ($live.session_start) { $live.session_start } else { (Get-Date).ToUniversalTime().ToString('o') }
    gates_total = if ($live.gates_total) { [int]$live.gates_total } else { 145 }
    gates_pass_count = $passed.Count
    resume_from_gate = $resumeGate
    gates_passed = $passed
    playwright_partial = if ($pw -and $pw.active) {
        [ordered]@{
            gate = $resumeGate
            test_index = [int]$pw.test_index
            test_total = [int]$pw.test_total
            last_line = [string]$pw.last_line
            note = 'Playwright ui-ux-* tiers resume via UI_TESTS_START_AFTER_INDEX on checkpoint restore.'
        }
    } else { $null }
    resume_command = '.\scripts\vpp\Resume-VppMonitoredLabRun.ps1 -SkipStackPrep'
}

if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
$doc | ConvertTo-Json -Depth 8 | Set-Content -Path $OutPath -Encoding utf8

$update = Join-Path $PSScriptRoot 'Update-VppLiveProgress.ps1'
if (Test-Path $update) {
    $gHash = @{}
    foreach ($k in $passed.Keys) { $gHash[$k] = 'PASS' }
    & $update -Level $doc.level -Attempt $doc.attempt -Gates $gHash -CurrentGate $resumeGate -Phase 'paused'
}

Write-Host "[VPP checkpoint] $($passed.Count)/$($doc.gates_total) PASS -> resume gate: $resumeGate" -ForegroundColor Green
Write-Host "  File: $OutPath" -ForegroundColor DarkGray
if ($doc.playwright_partial) {
    Write-Host "  PW partial: $($doc.playwright_partial.test_index)/$($doc.playwright_partial.test_total) (resume via UI_TESTS_START_AFTER_INDEX)" -ForegroundColor DarkYellow
}
