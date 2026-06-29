#Requires -Version 5.1
# Real-time VPP feed: new gate PASS/FAIL + Playwright N/total every N seconds.
param(
    [int]$IntervalSec = 0,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

if ($Help) {
    Write-Host @"
Usage: .\scripts\vpp\Start-VppRealtimeWatch.ps1 [-IntervalSec 30]

Writes:
  deploy/qemu/run/vpp-evidence/vpp-realtime-feed.txt  (human, tail -f)
  deploy/qemu/run/vpp-evidence/vpp-realtime-events.jsonl (machine)

Env: VPP_REALTIME_SEC (default 30)
"@
    exit 0
}

if ($IntervalSec -le 0) {
    $IntervalSec = 30
    if ($env:VPP_REALTIME_SEC) {
        $n = 0
        if ([int]::TryParse($env:VPP_REALTIME_SEC, [ref]$n) -and $n -gt 0) { $IntervalSec = $n }
    }
}

$gateJsonl = Join-Path $EvDir 'vpp-gate-events.jsonl'
$feedTxt = Join-Path $EvDir 'vpp-realtime-feed.txt'
$feedJsonl = Join-Path $EvDir 'vpp-realtime-events.jsonl'
$offsetPath = Join-Path $EvDir 'vpp-realtime-gate-offset.txt'
$pwScript = Join-Path $Root 'scripts\vpp\Get-VppPlaywrightProgress.ps1'
$tickScript = Join-Path $Root 'scripts\vpp\Write-VppStatusTick.ps1'
$greenPath = Join-Path $EvDir 'vpp-green.json'

$gateOffset = 0
if (Test-Path $offsetPath) {
    $gateOffset = [int](Get-Content -LiteralPath $offsetPath -Raw -ErrorAction SilentlyContinue)
}

function Write-Feed([string]$Line) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'
    $full = "$ts $Line"
    Add-Content -LiteralPath $feedTxt -Value $full -Encoding utf8
    Write-Host $full
}

function Get-HealthCode([string]$Url) {
    try { return (Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5).StatusCode } catch { return 'DOWN' }
}

$header = "=== VPP realtime watch START $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff') interval=${IntervalSec}s ==="
Set-Content -LiteralPath $feedTxt -Value $header -Encoding utf8
Write-Host $header -ForegroundColor Cyan

$lastPwIndex = -1
$lastGateKey = ''

while ($true) {
    try {
        . (Join-Path $Root 'scripts\vpp\Test-VppGreenValid.ps1')
        $sessionStart = Get-Date
        $sessPath = Join-Path $EvDir 'vpp-monitor-session.json'
        if (Test-Path $sessPath) {
            try {
                $sess = Get-Content -Raw $sessPath | ConvertFrom-Json
                if ($sess.session_id) { $sessionStart = [datetime]::Parse($sess.session_id) }
            } catch { }
        }
        $greenObj = $null
        if (Test-Path $greenPath) {
            try { $greenObj = Get-Content -Raw $greenPath | ConvertFrom-Json } catch { }
        }
        if (Test-VppComprehensiveGreen -Green $greenObj -SessionStart $sessionStart) {
            Write-Feed '[GREEN] comprehensive 145/145 — stopping realtime watch'
            break
        }

        if (Test-Path $gateJsonl) {
            $lines = @(Get-Content -LiteralPath $gateJsonl -Encoding utf8 -ErrorAction SilentlyContinue)
            if ($gateOffset -lt $lines.Count) {
                foreach ($line in $lines[$gateOffset..($lines.Count - 1)]) {
                    if (-not $line.Trim()) { continue }
                    try {
                        $ev = $line | ConvertFrom-Json
                        $icon = switch ($ev.status) {
                            'PASS' { '[OK]' }
                            'FAIL' { '[FAIL]' }
                            'RETRY' { '[RETRY]' }
                            default { '[GATE]' }
                        }
                        $detail = if ($ev.detail) { " | $($ev.detail)" } else { '' }
                        Write-Feed "$icon $($ev.gate) $($ev.status) $($ev.pass_count)/$($ev.total_gates)$detail"
                        $obj = [ordered]@{
                            at_local = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')
                            type = 'gate'
                            gate = $ev.gate
                            status = $ev.status
                            pass_count = $ev.pass_count
                        }
                        Add-Content -LiteralPath $feedJsonl -Value ($obj | ConvertTo-Json -Compress) -Encoding utf8
                    } catch {
                        Write-Feed "[warn] gate parse: $line"
                    }
                }
                $gateOffset = $lines.Count
                Set-Content -LiteralPath $offsetPath -Value $gateOffset -Encoding utf8 -NoNewline
            }
        }

        $pw = $null
        if (Test-Path $pwScript) {
            $pw = & $pwScript
        }
        $prog = $null
        $progPath = Join-Path $EvDir 'vpp-live-progress.json'
        if (Test-Path $progPath) {
            try { $prog = Get-Content -Raw $progPath | ConvertFrom-Json } catch { }
        }

        $api = Get-HealthCode 'http://127.0.0.1:18080/api/v1/health'
        $web = Get-HealthCode 'http://127.0.0.1:19088/'

        $pwLine = ''
        if ($pw -and $pw.active -and $pw.test_index -gt 0) {
            $tot = if ($pw.test_total -gt 0) { $pw.test_total } else { '?' }
            $pct = if ($pw.test_total -gt 0) { [math]::Round(($pw.test_index / $pw.test_total) * 100, 1) } else { 0 }
            $pwLine = "PW $($pw.test_index)/$tot ($pct%)"
            if ($pw.test_index -ne $lastPwIndex) {
                Write-Feed "[PW] $pwLine stall_log=$($pw.progress_stall_min)m same_test=$($pw.same_test_min)m"
                $lastPwIndex = $pw.test_index
            }
            if ($pw.progress_stall_min -ge 15 -or ($pw.same_test_min -ne $null -and $pw.same_test_min -ge 30)) {
                Write-Feed "[ALERT] Playwright stall: log_stall=$($pw.progress_stall_min)m same_test=$($pw.same_test_min)m index=$($pw.test_index)"
            }
        }

        $gateKey = if ($prog) { "$($prog.current_gate)|$($prog.gates_pass)" } else { '?' }
        if ($gateKey -ne $lastGateKey) {
            $gp = if ($prog) { "$($prog.gates_pass)/$($prog.gates_total)" } else { '?/?' }
            Write-Feed "[TICK] gates=$gp gate=$($prog.current_gate) API=$api WEB=$web $(if ($pwLine) { $pwLine })"
            $lastGateKey = $gateKey
        }

        $runnerAlive = $false
        if (Test-Path $sessPath) {
            try {
                $sess = Get-Content -Raw $sessPath | ConvertFrom-Json
                if ($sess.runner_pid -gt 0) {
                    $runnerAlive = $null -ne (Get-Process -Id $sess.runner_pid -ErrorAction SilentlyContinue)
                }
            } catch { }
        }
        if (-not $runnerAlive) {
            $vppProc = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
                Where-Object { $_.CommandLine -like '*run-vpp-until-green*' -or $_.CommandLine -like '*Start-VppMonitoredLabRun*' }
            $runnerAlive = ($null -ne $vppProc)
        }
        if (-not $runnerAlive -and $prog -and $prog.phase -eq 'running') {
            Write-Feed '[ALERT] VPP runner not found while phase=running'
        }
    } catch {
        Write-Feed "[ERR] realtime watch: $_"
    }
    Start-Sleep -Seconds $IntervalSec
}
