#Requires -Version 5.1
# After gate PASS in jsonl, update checkpoint and resume monitored VPP.
param(
    [Parameter(Mandatory)][string]$GateId,
    [int]$PollSec = 45,
    [int]$MaxMinutes = 360,
    [string]$RerunLog = "",
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$eventsPath = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-gate-events.jsonl'
$rerunLog = if ($RerunLog) { $RerunLog } else { Join-Path $Root 'deploy\qemu\run\vpp-gate-rerun-l4pp.log' }
$rerunStartedAfter = $null
if (Test-Path $rerunLog) {
    try { $rerunStartedAfter = (Get-Item -LiteralPath $rerunLog).LastWriteTime.AddMinutes(-5) } catch { }
}
$deadline = (Get-Date).AddMinutes($MaxMinutes)

function Test-GateRerunLogOk {
    if (-not (Test-Path $rerunLog)) { return $false }
    $tail = @(Get-Content -LiteralPath $rerunLog -Tail 20 -ErrorAction SilentlyContinue) -join "`n"
    return ($tail -match '\[OK\] playwright qemu matrix profile=L4\+\+')
}

function Test-GateRerunLogFail {
    if (-not (Test-Path $rerunLog)) { return $false }
    $tail = @(Get-Content -LiteralPath $rerunLog -Tail 40 -ErrorAction SilentlyContinue) -join "`n"
    return ($tail -match '(?i)FAIL tier|FAIL playwright|\[FAIL\]')
}

function Start-VppResumeAfterGate {
    $cpPath = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-checkpoint.json'
    if (Test-Path $cpPath) {
        $cp = Get-Content -Raw $cpPath | ConvertFrom-Json
        $passed = @{}
        foreach ($p in $cp.gates_passed.PSObject.Properties) { $passed[$p.Name] = $p.Value }
        $passed[$GateId] = 'PASS'
        $manifestPath = Join-Path $Root 'specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json'
        $nextGate = 'playwright_outer_full'
        if (Test-Path $manifestPath) {
            $m = Get-Content -Raw $manifestPath | ConvertFrom-Json
            $ordered = @($m.comprehensive_gates_ordered | Where-Object { $_ -ne 'coverage_report' })
            $seen = $false
            foreach ($g in $ordered) {
                if ($seen -and -not $passed.ContainsKey($g)) { $nextGate = $g; break }
                if ($g -eq $GateId) { $seen = $true }
            }
        }
        [ordered]@{
            schema_version = 1; spec = '030-vpp-product-verification'
            saved_at = (Get-Date).ToUniversalTime().ToString('o')
            saved_at_local = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')
            reason = "verified $GateId PASS (isolated rerun)"
            level = 'full'; attempt = 1; gates_total = 145
            gates_pass_count = $passed.Count
            resume_from_gate = $nextGate
            gates_passed = $passed
            resume_command = '.\scripts\vpp\Resume-VppMonitoredLabRun.ps1 -SkipStackPrep'
        } | ConvertTo-Json -Depth 8 | Set-Content -Path $cpPath -Encoding utf8
        $update = Join-Path $PSScriptRoot 'Update-VppLiveProgress.ps1'
        if (Test-Path $update) {
            & $update -Level 'full' -Attempt 1 -Gates $passed -CurrentGate $nextGate -Phase 'paused'
        }
        Write-Host "[wait-resume] checkpoint $($passed.Count)/145; next=$nextGate" -ForegroundColor Green
    }
    $env:KORUS_QEMU_THREE_VM = '1'
    $env:VPP_REALTIME_SEC = '30'
    $env:VPP_INLINE_GATE_MAX = '5'
    & (Join-Path $PSScriptRoot 'Stop-VppLabRun.ps1') -Force | Out-Null
    $lockScript = Join-Path $PSScriptRoot 'Invoke-VppLabRunLock.ps1'
    if (Test-Path $lockScript) { & $lockScript -Action Exit -Force | Out-Null }
    & (Join-Path $PSScriptRoot 'Resume-VppMonitoredLabRun.ps1') -SkipStackPrep -TickSec 60
    exit $LASTEXITCODE
}

if ($Help) {
    Write-Host 'Usage: Wait-AndResumeAfterGate.ps1 -GateId playwright_matrix_l4_plus_plus'
    exit 0
}

Write-Host "[wait-resume] gate=$GateId PASS -> Resume-VppMonitoredLabRun" -ForegroundColor Cyan
while ((Get-Date) -lt $deadline) {
    if (Test-GateRerunLogOk) {
        Write-Host "[wait-resume] $GateId verified OK in rerun log" -ForegroundColor Green
        Start-VppResumeAfterGate
    }
    if (Test-GateRerunLogFail) {
        Write-Host "[wait-resume] $GateId FAIL in rerun log" -ForegroundColor Red
        & (Join-Path $PSScriptRoot 'Save-VppCheckpoint.ps1') -Reason "isolated rerun FAIL $GateId" -ResumeGate $GateId | Out-Null
        exit 1
    }
    if (Test-Path $eventsPath) {
        # gate-events ignored — stale PASS caused false resume; rely on rerun log only.
    }
    $pw = $null
    try { $pw = & (Join-Path $PSScriptRoot 'Get-VppPlaywrightProgress.ps1') } catch { }
    if ($pw -and $pw.active) {
        Write-Host ("[wait-resume] $(Get-Date -Format HH:mm:ss) PW {0}/{1}" -f $pw.test_index, $pw.test_total) -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds $PollSec
}
Write-Host "[wait-resume] TIMEOUT" -ForegroundColor Red
exit 2
