#Requires -Version 5.1
# Human-readable VPP status snapshot for live chat reports (spec 030).
param(
    [string]$TickLogPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) { Write-Host "Usage: Write-VppStatusTick.ps1"; exit 0 }

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"
$RunDir = Join-Path $Root "deploy\qemu\run"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
if (-not $TickLogPath) { $TickLogPath = Join-Path $EvDir "vpp-status-ticks.jsonl" }

$sessionScript = Join-Path $Root "scripts\vpp\Get-VppMonitorSession.ps1"
$session = if (Test-Path $sessionScript) { & $sessionScript -Action NextTick } else { $null }
$tickNum = if ($session) { [int]$session.tick_number } else { 0 }
$tickLocal = (Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff")

function Get-Health([string]$Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return $r.StatusCode
    } catch { return "DOWN" }
}

$progress = $null
$progPath = Join-Path $EvDir "vpp-live-progress.json"
if (Test-Path $progPath) {
    try { $progress = Get-Content -Raw $progPath | ConvertFrom-Json } catch { }
}

$failure = $null
$failLatest = Join-Path $EvDir "vpp-failure-analysis-latest.json"
if (Test-Path $failLatest) {
    try { $failure = Get-Content -Raw $failLatest | ConvertFrom-Json } catch { }
}

$green = $null
$greenPath = Join-Path $EvDir "vpp-green.json"
if (Test-Path $greenPath) {
    try { $green = Get-Content -Raw $greenPath | ConvertFrom-Json } catch { }
}

$sessionStart = if ($progress -and $progress.session_start) { [datetime]::Parse($progress.session_start) } elseif ($session -and $session.session_id) { [datetime]::Parse($session.session_id) } else { Get-Date }
. (Join-Path $Root "scripts\vpp\Test-VppGreenValid.ps1")
$isComprehensiveGreen = Test-VppComprehensiveGreen -Green $green -SessionStart $sessionStart

$fixes = @()
$fixesLog = Join-Path $EvDir "vpp-fixes-log.jsonl"
if (Test-Path $fixesLog) {
    $fixes = @(Get-Content $fixesLog -ErrorAction SilentlyContinue | ForEach-Object {
        try { $_ | ConvertFrom-Json } catch { $null }
    } | Where-Object { $_ })
}

$elapsedMin = [math]::Round(((Get-Date).ToUniversalTime() - $sessionStart.ToUniversalTime()).TotalMinutes, 1)
$gateStallMin = $null
if ($progress -and $progress.updated_at -and $progress.current_gate) {
    try {
        $lastUpd = [datetime]::Parse($progress.updated_at)
        $gateStallMin = [math]::Round(((Get-Date).ToUniversalTime() - $lastUpd.ToUniversalTime()).TotalMinutes, 1)
    } catch { }
}

$pwProgress = $null
$pwScript = Join-Path $Root 'scripts\vpp\Get-VppPlaywrightProgress.ps1'
if (Test-Path $pwScript) {
    try { $pwProgress = & $pwScript } catch { }
}

$stackWait = $null
$stackWaitPath = Join-Path $RunDir "stack-wait-live.json"
if (Test-Path $stackWaitPath) {
    try { $stackWait = Get-Content -Raw $stackWaitPath | ConvertFrom-Json } catch { }
}

$phaseLine = ''
$phasePath = Join-Path $EvDir "vpp-monitor-phase.txt"
if (Test-Path $phasePath) {
    $phaseLine = (Get-Content -LiteralPath $phasePath -Raw -ErrorAction SilentlyContinue).Trim()
}

$passedList = @()
$failedList = @()
if ($progress -and $progress.gates) {
    foreach ($p in $progress.gates.PSObject.Properties) {
        if ($p.Value -eq "PASS") { $passedList += $p.Name }
        elseif ($p.Value -eq "FAIL") { $failedList += $p.Name }
    }
}
$passedList = $passedList | Sort-Object
$failedList = $failedList | Sort-Object

$recentPassed = if ($passedList.Count -gt 8) { $passedList[-8..-1] } else { $passedList }

$stallThresholdMin = 10
if ($env:VPP_STALL_THRESHOLD_MIN) {
    $parsed = 0
    if ([int]::TryParse($env:VPP_STALL_THRESHOLD_MIN, [ref]$parsed) -and $parsed -gt 0) { $stallThresholdMin = $parsed }
}
$pwStallThresholdMin = 15
if ($env:VPP_PW_STALL_THRESHOLD_MIN) {
    $parsed = 0
    if ([int]::TryParse($env:VPP_PW_STALL_THRESHOLD_MIN, [ref]$parsed) -and $parsed -gt 0) { $pwStallThresholdMin = $parsed }
}
$playwrightStalled = $false
if ($pwProgress -and $pwProgress.active) {
    if ($pwProgress.progress_stall_min -ne $null -and $pwProgress.progress_stall_min -ge $pwStallThresholdMin) {
        $playwrightStalled = $true
    }
    if ($pwProgress.same_test_min -ne $null -and $pwProgress.same_test_min -ge 30) {
        $playwrightStalled = $true
    }
}
$isStalled = ($progress -and $progress.phase -eq "running") -and (
    ($playwrightStalled) -or (
        ($gateStallMin -ne $null -and $gateStallMin -ge $stallThresholdMin) -and (-not $pwProgress -or -not $pwProgress.active)
    )
)

$tick = [ordered]@{
    tick_number = $tickNum
    tick_at = (Get-Date).ToUniversalTime().ToString("o")
    tick_at_local = $tickLocal
    elapsed_min = $elapsedMin
    status = if ($isComprehensiveGreen) { "GREEN" } elseif ($isStalled) { "STALLED" } elseif ($progress.phase -eq "failed") { "FAILED" } elseif ($progress) { "RUNNING" } else { "STARTING" }
    attempt = if ($progress) { $progress.attempt } else { 1 }
    gates_pass = if ($progress) { $progress.gates_pass } else { 0 }
    gates_fail = if ($progress) { $progress.gates_fail } else { 0 }
    gates_total = if ($progress) { $progress.gates_total } else { 145 }
    current_gate = if ($progress) { $progress.current_gate } else { "?" }
    last_failed_gate = if ($progress) { $progress.last_failed_gate } else { "" }
    health_api = Get-Health "http://127.0.0.1:18080/api/v1/health"
    health_web = Get-Health "http://127.0.0.1:19088/"
    recent_passed_gates = @($recentPassed)
    failed_gates = @($failedList)
    fixes_since_start = @($fixes)
    failure_analysis = if ($failure) { $failure.last_failed_gate } else { $null }
    gate_stall_min = $gateStallMin
    playwright_test_index = if ($pwProgress) { $pwProgress.test_index } else { 0 }
    playwright_test_total = if ($pwProgress) { $pwProgress.test_total } else { 0 }
    playwright_log_stall_min = if ($pwProgress) { $pwProgress.progress_stall_min } else { $null }
    playwright_same_test_min = if ($pwProgress) { $pwProgress.same_test_min } else { $null }
    stack_wait_phase = if ($stackWait) { $stackWait.phase } else { $null }
    stack_wait_maintenance_min = if ($stackWait.maintenance_min) { $stackWait.maintenance_min } else { $null }
    stack_wait_busy = if ($stackWait.busy_reasons) { @($stackWait.busy_reasons) } else { @() }
    monitor_phase = $phaseLine
}

$tickJson = ($tick | ConvertTo-Json -Depth 6 -Compress)
Add-Content -Path $TickLogPath -Value $tickJson -Encoding utf8

$pct = if ($tick.gates_total -gt 0) { [math]::Round(($tick.gates_pass / $tick.gates_total) * 100, 1) } else { 0 }
$stackWaitLine = ""
if ($stackWait -and $stackWait.phase -eq 'maintenance') {
    $stackWaitLine = "Stack wait: maintenance $([math]::Round($stackWait.maintenance_min, 1))m - $($stackWait.busy_reasons -join '; ')"
} elseif ($stackWait -and $stackWait.phase -eq 'waiting') {
    $stackWaitLine = "Stack wait: preflight - $($stackWait.issues -join '; ')"
}
Write-Output @"
=== VPP tick #$('{0:D3}' -f $tickNum) $tickLocal ===
Status: $($tick.status) | attempt $($tick.attempt) | elapsed ${elapsedMin}m
$(if ($phaseLine) { "Phase: $phaseLine" })
Progress: $($tick.gates_pass)/$($tick.gates_total) PASS ($pct%) | fail=$($tick.gates_fail)
Current gate: $($tick.current_gate)$(if ($isStalled) { " [STALLED]" } elseif ($pwProgress -and $pwProgress.active -and $pwProgress.test_index -gt 0) { " [PW $($pwProgress.test_index)/$($pwProgress.test_total)]" })
$(if ($pwProgress -and $pwProgress.active -and $pwProgress.last_line) { "Playwright: $($pwProgress.last_line)" })
Health: API=$($tick.health_api) WEB=$($tick.health_web)
$(if ($stackWaitLine) { $stackWaitLine })
Recent PASS: $(if ($recentPassed.Count) { ($recentPassed -join ', ') } else { '(none yet)' })
$(if ($failedList.Count) { "FAIL gates: $($failedList -join ', ')" })
$(if ($failure) { "Last analysis: $($failure.last_failed_gate) (attempt $($failure.attempt))" })
$(if ($fixes.Count) { "Fixes logged: $($fixes.Count)" } else { "Fixes logged: 0" })
$(if ($isComprehensiveGreen) { "GREEN marker: vpp-green.json full_coverage=$($green.full_coverage) ($($green.gates_pass)/$($green.gates_total))" })
$(if ($green -and -not $isComprehensiveGreen) { "NOTE: stale vpp-green.json ignored ($($green.gates_pass)/$($green.gates_total) level=$($green.level))" })
"@
