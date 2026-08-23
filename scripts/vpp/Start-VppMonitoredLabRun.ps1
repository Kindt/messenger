#Requires -Version 5.1
# Stop all lab jobs, fresh session, numbered status ticks + hung-process cleanup + VPP full.
param(
    [ValidateSet('full', 'standard')]
    [string]$Level = 'full',
    [int]$MaxAttempts = 10,
    [int]$TickSec = 0,
    [switch]$SinglePass,
    [switch]$SkipStackPrep,
    [switch]$NoStop,
    [switch]$Resume,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ($Help) {
    Write-Host @"
Usage: .\scripts\vpp\Start-VppMonitoredLabRun.ps1 [-Level full] [-TickSec 300]

1. Stop-VppLabRun (kill parallel jobs / plink)
2. Fresh monitor session + archived progress
3. Background status watcher (numbered ticks, hung cleanup)
4. run-vpp-until-green

Env: KORUS_QEMU_THREE_VM=1, VPP_STATUS_TICK_SEC=300, VPP_PLINK_MAX_MIN=45
"@
    exit 0
}

if (-not $NoStop) {
    Write-Host "=== VPP monitored lab: STOP ALL ===" -ForegroundColor Yellow
    & (Join-Path $Root 'scripts\vpp\Stop-VppLabRun.ps1') -Force
}

$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

$stamp = Get-Date -Format 'yyyy-MM-dd-HHmmss'
$archiveFiles = @('vpp-live-progress.json', 'vpp-chat-latest.txt', 'vpp-ticks-console.log', 'vpp-status-ticks.jsonl', 'vpp-green.json', 'vpp-gate-events.jsonl', 'vpp-chat-gates.txt')
if ($Resume) {
    $archiveFiles = @('vpp-chat-latest.txt', 'vpp-green.json')
    Write-Host "[VPP] resume mode - keeping checkpoint + gate progress artifacts" -ForegroundColor Cyan
}
foreach ($f in $archiveFiles) {
    $p = Join-Path $EvDir $f
    if (Test-Path $p) {
        Copy-Item $p (Join-Path $EvDir ("archive-$stamp-" + (Split-Path $p -Leaf))) -Force
        Remove-Item $p -Force
    }
}

& (Join-Path $Root 'scripts\vpp\Get-VppMonitorSession.ps1') -Action Init | Out-Null

if ($TickSec -le 0) {
    $TickSec = 300
    if ($env:VPP_STATUS_TICK_SEC) {
        $n = 0
        if ([int]::TryParse($env:VPP_STATUS_TICK_SEC, [ref]$n) -and $n -gt 0) { $TickSec = $n }
    }
}

$env:KORUS_QEMU_THREE_VM = '1'
$env:VPP_STATUS_TICK_SEC = "$TickSec"
$env:VPP_INLINE_GATE_RETRY = '1'
$env:VPP_INLINE_GATE_MAX = if ($env:VPP_INLINE_GATE_MAX) { $env:VPP_INLINE_GATE_MAX } else { '5' }
$env:VPP_RETRY_DELAY_SEC = if ($env:VPP_RETRY_DELAY_SEC) { $env:VPP_RETRY_DELAY_SEC } else { '60' }
$env:VPP_CHAT_REPORT_SEC = if ($env:VPP_CHAT_REPORT_SEC) { $env:VPP_CHAT_REPORT_SEC } else { "$TickSec" }
if ($SinglePass) { $MaxAttempts = 1 }

$lockScript = Join-Path $Root 'scripts\vpp\Invoke-VppLabRunLock.ps1'
if (Test-Path $lockScript) {
    & $lockScript -Action Enter -TaskName "monitored-$Level"
    if ($LASTEXITCODE -eq 2) { exit 2 }
}

$sessionScript = Join-Path $Root 'scripts\vpp\Get-VppMonitorSession.ps1'
$watcher = Join-Path $Root 'scripts\vpp\Start-VppStatusWatcher.ps1'
$reporter = Join-Path $Root 'scripts\vpp\Start-VppChatReporter.ps1'
$realtime = Join-Path $Root 'scripts\vpp\Start-VppRealtimeWatch.ps1'

$realtimeSec = 30
if ($env:VPP_REALTIME_SEC) {
    $n = 0
    if ([int]::TryParse($env:VPP_REALTIME_SEC, [ref]$n) -and $n -gt 0) { $realtimeSec = $n }
}
if (-not $env:VPP_CHAT_REPORT_SEC) { $env:VPP_CHAT_REPORT_SEC = "$realtimeSec" }

$watcherProc = Start-Process -FilePath 'powershell.exe' -PassThru -WindowStyle Hidden -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $watcher, '-IntervalSec', $TickSec
)
$reporterProc = Start-Process -FilePath 'powershell.exe' -PassThru -WindowStyle Hidden -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $reporter
)
$realtimeProc = $null
if (Test-Path $realtime) {
    $realtimeProc = Start-Process -FilePath 'powershell.exe' -PassThru -WindowStyle Hidden -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $realtime, '-IntervalSec', $realtimeSec
    )
}
& $sessionScript -Action Set -Values @{
    watcher_pid = $watcherProc.Id; reporter_pid = $reporterProc.Id; runner_pid = $PID
    realtime_pid = $(if ($realtimeProc) { $realtimeProc.Id } else { 0 })
} | Out-Null

Write-Host "[VPP] watcher pid $($watcherProc.Id) tick every ${TickSec}s" -ForegroundColor Cyan
Write-Host "[VPP] reporter pid $($reporterProc.Id)" -ForegroundColor Cyan
if ($realtimeProc) {
    Write-Host "[VPP] realtime pid $($realtimeProc.Id) every ${realtimeSec}s -> vpp-realtime-feed.txt" -ForegroundColor Cyan
}
Write-Host "[VPP] ticks log: deploy/qemu/run/vpp-evidence/vpp-ticks-console.log" -ForegroundColor DarkGray

$code = 1
try {
if (-not $SkipStackPrep) {
    & (Join-Path $Root 'scripts\vpp\Set-VppMonitorPhase.ps1') -Phase 'prep:stack'
    $api = try { (Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/health' -TimeoutSec 5).status } catch { 'DOWN' }
    if ($api -ne 'ok') {
        Write-Host '[prep] API down - qemu-sync-api-core + Wait-KorusLabStackReady...' -ForegroundColor Yellow
        & (Join-Path $Root 'scripts\vpp\Set-VppMonitorPhase.ps1') -Phase 'prep:api-rebuild'
        & (Join-Path $Root 'scripts\Wait-KorusLabStackReady.ps1') -MaxMinutes 90 -LaunchRebuildIfNeeded -WarmIfDown
        if ($LASTEXITCODE -ne 0) { throw 'API not ready after prep (Wait-KorusLabStackReady)' }
    }
    & (Join-Path $Root 'scripts\vpp\Set-VppMonitorPhase.ps1') -Phase 'prep:integrations'
    $intSsh = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if (-not $intSsh.TcpTestSucceeded) {
        Write-Host '[prep] integrations down - qemu-integrations-up...' -ForegroundColor Yellow
        & (Join-Path $Root 'scripts\qemu-integrations-up.ps1') 2>&1 | Out-Host
    }
    & (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') -MaxSec 1800 -StartVmIfDown -RepairGateway
    if ($LASTEXITCODE -ne 0) { throw 'integrations not ready after prep (30 min)' }
}

& (Join-Path $Root 'scripts\vpp\Set-VppMonitorPhase.ps1') -Phase ''
. (Join-Path $Root 'deploy\qemu\lib\Get-KorusPythonCmd.ps1')
$py = Get-KorusPythonCmd
& $py (Join-Path $Root 'scripts\vpp\build_gate_labels_ru.py') 2>&1 | Out-Null
& (Join-Path $Root 'scripts\vpp\Write-VppChatReport.ps1') | Out-Null
& (Join-Path $Root 'scripts\vpp\Write-VppStatusTick.ps1')

$log = Join-Path $Root 'deploy\qemu\run\vpp-until-green.log'
$startLocal = Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'
Write-Host "=== VPP monitored run START $startLocal level=$Level ===" -ForegroundColor Green

    $untilArgs = @{ Level = $Level; MaxAttempts = $MaxAttempts }
    if ($Resume -or $env:VPP_RESUME_CHECKPOINT) {
        $untilArgs['ResumeCheckpoint'] = if ($env:VPP_RESUME_CHECKPOINT) { $env:VPP_RESUME_CHECKPOINT } else {
            Join-Path $EvDir 'vpp-checkpoint.json'
        }
    }
    & (Join-Path $Root 'scripts\run-vpp-until-green.ps1') @untilArgs 2>&1 | Tee-Object -FilePath $log -Append
    $code = $LASTEXITCODE
} catch {
    Write-Host "[FAIL] $($_.Exception.Message)" -ForegroundColor Red
    & (Join-Path $Root 'scripts\vpp\Set-VppMonitorPhase.ps1') -Phase "failed:$($_.Exception.Message)"
    & (Join-Path $Root 'scripts\vpp\Write-VppStatusTick.ps1') | Out-Host
    $code = 1
} finally {
    if ($code -eq 0 -and (Test-Path (Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-green.json'))) {
        foreach ($p in @($watcherProc, $reporterProc)) {
            if ($p -and -not $p.HasExited) {
                Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }
    if (Test-Path $lockScript) { & $lockScript -Action Exit -Force | Out-Null }
}
exit $code
