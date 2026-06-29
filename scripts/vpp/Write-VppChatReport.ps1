#Requires -Version 5.1
# Russian VPP progress report for chat (spec 030). Writes vpp-chat-latest.txt + jsonl tick.
param(
    [string]$ChatOutPath = "",
    [string]$TickLogPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Write-VppChatReport.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $Root "scripts\vpp\Get-VppGateLabelRu.ps1")

$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"
$RunDir = Join-Path $Root "deploy\qemu\run"
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
if (-not $ChatOutPath) { $ChatOutPath = Join-Path $EvDir "vpp-chat-latest.txt" }
if (-not $TickLogPath) { $TickLogPath = Join-Path $EvDir "vpp-status-ticks.jsonl" }

function Get-Health([string]$Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        return [string]$r.StatusCode
    } catch { return "DOWN" }
}

function Get-LastLogError([string]$LogPath, [int]$TailLines = 40) {
    if (-not (Test-Path $LogPath)) { return $null }
    $lines = @(Get-Content -LiteralPath $LogPath -Tail $TailLines -ErrorAction SilentlyContinue)
    $fail = @($lines | Where-Object { $_ -match '(?i)\[FAIL\]|FAIL |error|throw|Exception' })
    if ($fail.Count -gt 0) { return ($fail[-1..([math]::Max(0, $fail.Count - 3))] -join " | ") }
    return $null
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

$planFail = $null
$planPath = Join-Path $RunDir "plan-failure-analysis.json"
if (Test-Path $planPath) {
    try { $planFail = Get-Content -Raw $planPath | ConvertFrom-Json } catch { }
}

$green = $null
$greenPath = Join-Path $EvDir "vpp-green.json"
if (Test-Path $greenPath) {
    try { $green = Get-Content -Raw $greenPath | ConvertFrom-Json } catch { }
}

$stackWait = $null
$stackWaitPath = Join-Path $RunDir "stack-wait-live.json"
if (Test-Path $stackWaitPath) {
    try { $stackWait = Get-Content -Raw $stackWaitPath | ConvertFrom-Json } catch { }
}

$sessionScript = Join-Path $Root "scripts\vpp\Get-VppMonitorSession.ps1"
$monSession = if (Test-Path $sessionScript) { & $sessionScript -Action Get } else { $null }
$tickNum = if ($monSession) { [int]$monSession.tick_number } else { 0 }
$tickLocal = if ($monSession -and $monSession.last_tick_at_local) { $monSession.last_tick_at_local } else { (Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff") }

$sessionStart = if ($progress -and $progress.session_start) { [datetime]::Parse($progress.session_start) } elseif ($monSession -and $monSession.session_id) { [datetime]::Parse($monSession.session_id) } else { Get-Date }
$elapsedMin = [math]::Round(((Get-Date).ToUniversalTime() - $sessionStart.ToUniversalTime()).TotalMinutes, 1)
$elapsedRu = Format-VppElapsedRu -Minutes $elapsedMin

. (Join-Path $Root "scripts\vpp\Test-VppGreenValid.ps1")
$isComprehensiveGreen = Test-VppComprehensiveGreen -Green $green -SessionStart $sessionStart

$gatesTotal = if ($progress) { [int]$progress.gates_total } else { 145 }
$gatesPass = if ($progress) { [int]$progress.gates_pass } else { 0 }
$gatesFail = if ($progress) { [int]$progress.gates_fail } else { 0 }
$gatesSkip = if ($progress) { [int]$progress.gates_skip } else { 0 }
$gatesRemain = [math]::Max(0, $gatesTotal - $gatesPass - $gatesFail - $gatesSkip)
$pct = if ($gatesTotal -gt 0) { [math]::Round(($gatesPass / $gatesTotal) * 100, 1) } else { 0 }

$passedList = @()
$failedList = @()
$skippedList = @()
if ($progress -and $progress.gates) {
    foreach ($p in $progress.gates.PSObject.Properties) {
        switch ($p.Value) {
            "PASS" { $passedList += $p.Name }
            "FAIL" { $failedList += $p.Name }
            "SKIP" { $skippedList += $p.Name }
        }
    }
}

$gateStallMin = $null
if ($progress -and $progress.updated_at -and $progress.current_gate) {
    try {
        $lastUpd = [datetime]::Parse($progress.updated_at)
        $gateStallMin = [math]::Round(((Get-Date).ToUniversalTime() - $lastUpd.ToUniversalTime()).TotalMinutes, 1)
    } catch { }
}
$stallThresholdMin = 10
if ($env:VPP_STALL_THRESHOLD_MIN) {
    $parsed = 0
    if ([int]::TryParse($env:VPP_STALL_THRESHOLD_MIN, [ref]$parsed) -and $parsed -gt 0) { $stallThresholdMin = $parsed }
}
$isStalled = ($gateStallMin -ne $null -and $gateStallMin -ge $stallThresholdMin -and $progress.phase -eq "running")

$statusKey = if ($isComprehensiveGreen) { "GREEN" } elseif ($isStalled) { "STALLED" } elseif ($progress.phase -eq "failed") { "FAILED" } elseif ($progress) { "RUNNING" } else { "STARTING" }
$statusRu = Get-VppStatusRu -Status $statusKey
$s = Get-VppChatStringsRu
$attempt = if ($progress) { $progress.attempt } else { 1 }
$currentGate = if ($progress) { $progress.current_gate } else { "?" }
$currentGateRu = if ($currentGate -and $currentGate -ne "?") { Get-VppGateLabelRu -GateId $currentGate } else { [string]$s.waiting_start }

$healthApi = Get-Health "http://127.0.0.1:18080/api/v1/health"
$healthWeb = Get-Health "http://127.0.0.1:19088/"

$errors = @()
if ($failedList.Count) {
    foreach ($g in $failedList) {
        $errors += "- $(Get-VppGateLabelRu -GateId $g) ($g)"
    }
}
if ($progress.last_failed_gate -and $failedList -notcontains $progress.last_failed_gate) {
    $g = $progress.last_failed_gate
    $errors += "- $(Get-VppGateLabelRu -GateId $g) ($g) [$($s.hint_last_fail)]"
}
if ($failure -and $failure.remediation) {
    foreach ($r in @($failure.remediation)) {
        if ($r.fix_hints -and @($r.fix_hints).Count -gt 0) {
            $hint = @($r.fix_hints)[0]
            $errors += "- $($s.hint_fix) ($($r.gate)): $hint"
        }
    }
}
if ($planFail -and $planFail.summaryRu) {
    $errors += "- $($s.hint_playwright): $($planFail.summaryRu)"
}
$logErr = Get-LastLogError (Join-Path $RunDir "vpp-until-green.log")
if ($logErr) { $errors += "- $($s.hint_log): $logErr" }
if ($stackWait -and $stackWait.phase -eq "maintenance") {
    $errors += "- $($s.hint_maintenance) $([math]::Round($stackWait.maintenance_min, 1)) min: $($stackWait.busy_reasons -join '; ')"
} elseif ($stackWait -and $stackWait.phase -eq "waiting" -and $stackWait.issues) {
    $errors += "- $($s.hint_stack): $($stackWait.issues -join '; ')"
}
if ($healthApi -eq "DOWN") { $errors += "- $($s.hint_api_down)" }
if ($healthWeb -eq "DOWN") { $errors += "- $($s.hint_web_down)" }
$errors = @($errors | Select-Object -Unique)

$recentPassedRu = @()
$tail = if ($passedList.Count -gt 6) { $passedList[-6..-1] } else { $passedList }
foreach ($g in $tail) { $recentPassedRu += "- $(Get-VppGateLabelRu -GateId $g)" }

$stallSuffix = if ($isStalled) { " [$($s.label_stall) $gateStallMin min]" } else { "" }

$lines = @(
    "## $($s.report_title) tick #$('{0:D3}' -f $tickNum) $tickLocal"
    ""
    "**$($s.label_status):** $statusRu | $($s.label_attempt) $attempt"
    "**$($s.label_progress):** $gatesPass / $gatesTotal $($s.label_passed) | **$gatesRemain $($s.label_remaining)** | fail=$gatesFail skip=$gatesSkip ($pct%)"
    "**$($s.label_time):** $elapsedRu ($($s.label_since_session))"
    "**$($s.label_current):** $currentGateRu$stallSuffix"
    "**$($s.label_stack):** API=$healthApi | Web=$healthWeb"
    ""
)
if ($errors.Count) {
    $lines += "### $($s.section_errors)"
    $lines += $errors
    $lines += ""
}
if ($recentPassedRu.Count) {
    $lines += "### $($s.section_recent_pass)"
    $lines += $recentPassedRu
    $lines += ""
}
if ($isComprehensiveGreen) {
    $lines += "**GREEN:** vpp-green.json ($($green.gates_pass)/$($green.gates_total) full)"
} elseif ($green) {
    $lines += "**NOTE:** stale vpp-green.json ignored ($($green.gates_pass)/$($green.gates_total) level=$($green.level))"
}

$report = ($lines -join "`n")
Set-Content -Path $ChatOutPath -Value $report -Encoding utf8

$tick = [ordered]@{
    tick_number = $tickNum
    tick_at = (Get-Date).ToUniversalTime().ToString("o")
    tick_at_local = $tickLocal
    elapsed_min = $elapsedMin
    status = $statusKey
    status_ru = $statusRu
    attempt = $attempt
    gates_pass = $gatesPass
    gates_fail = $gatesFail
    gates_skip = $gatesSkip
    gates_remaining = $gatesRemain
    gates_total = $gatesTotal
    pct = $pct
    current_gate = $currentGate
    current_gate_ru = $currentGateRu
    failed_gates = @($failedList)
    failed_gates_ru = @($failedList | ForEach-Object { Get-VppGateLabelRu -GateId $_ })
    health_api = $healthApi
    health_web = $healthWeb
    errors = @($errors)
    gate_stall_min = $gateStallMin
}
Add-Content -Path $TickLogPath -Value ($tick | ConvertTo-Json -Depth 6 -Compress) -Encoding utf8

Write-Output $report
