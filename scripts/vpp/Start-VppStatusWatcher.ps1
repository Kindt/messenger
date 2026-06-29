#Requires -Version 5.1
# Emit VPP status ticks every N minutes while run-vpp-until-green is active.
param(
    [int]$IntervalSec = 0,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Start-VppStatusWatcher.ps1 [-IntervalSec 60]"
    Write-Host "Env: VPP_STATUS_TICK_SEC (default 60; was 300)"
    exit 0
}

if ($IntervalSec -le 0) {
    $IntervalSec = 60
    if ($env:VPP_STATUS_TICK_SEC) {
        $parsed = 0
        if ([int]::TryParse($env:VPP_STATUS_TICK_SEC, [ref]$parsed) -and $parsed -gt 0) { $IntervalSec = $parsed }
    }
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tickScript = Join-Path $Root "scripts\vpp\Write-VppStatusTick.ps1"
$hungScript = Join-Path $Root "scripts\vpp\Invoke-VppHungProcessCleanup.ps1"
$greenPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-green.json"
$tickOut = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-ticks-console.log"

Write-Host "[VPP watcher] tick every ${IntervalSec}s (numbered, local time) -> vpp-status-ticks.jsonl" -ForegroundColor Cyan
while ($true) {
    try {
        if (Test-Path $hungScript) {
            & $hungScript -Quiet | Out-Null
        }
        $out = & $tickScript
        if ($out) {
            $out | Add-Content -Path $tickOut -Encoding utf8
            Write-Host $out
        }
    } catch {
        $errLine = "[VPP watcher] tick error at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'): $_"
        Write-Host $errLine -ForegroundColor Red
        $errLine | Add-Content -Path $tickOut -Encoding utf8
    }
    if (Test-Path $greenPath) {
        . (Join-Path $Root 'scripts\vpp\Test-VppGreenValid.ps1')
        $sessionPath = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-monitor-session.json'
        $sessionStart = Get-Date
        if (Test-Path $sessionPath) {
            try {
                $sess = Get-Content -Raw $sessionPath | ConvertFrom-Json
                if ($sess.session_id) { $sessionStart = [datetime]::Parse($sess.session_id) }
            } catch { }
        }
        $green = $null
        try { $green = Get-Content -Raw $greenPath | ConvertFrom-Json } catch { }
        if (Test-VppComprehensiveGreen -Green $green -SessionStart $sessionStart) {
            Write-Host "[VPP watcher] comprehensive GREEN detected, stopping watcher" -ForegroundColor Green
            break
        }
    }
    Start-Sleep -Seconds $IntervalSec
}
