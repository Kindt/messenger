#Requires -Version 5.1
# Emit Russian VPP chat reports every N seconds while until-green runs (default 5 min).
param(
    [int]$IntervalSec = 0,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\vpp\Start-VppChatReporter.ps1 [-IntervalSec 300]

Writes deploy/qemu/run/vpp-evidence/vpp-chat-latest.txt every tick.
Env: VPP_CHAT_REPORT_SEC (default 300 = 5 min)
"@
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$reportScript = Join-Path $Root "scripts\vpp\Write-VppChatReport.ps1"
$greenPath = Join-Path $Root "deploy\qemu\run\vpp-evidence\vpp-green.json"

if ($IntervalSec -le 0) {
    $IntervalSec = 300
    if ($env:VPP_CHAT_REPORT_SEC) {
        $parsed = 0
        if ([int]::TryParse($env:VPP_CHAT_REPORT_SEC, [ref]$parsed) -and $parsed -gt 0) { $IntervalSec = $parsed }
    }
}

Write-Host "[VPP chat] report every ${IntervalSec}s -> vpp-evidence/vpp-chat-latest.txt" -ForegroundColor Cyan
while ($true) {
    & $reportScript | Out-Null
    . (Join-Path $Root 'scripts\vpp\Test-VppGreenValid.ps1')
    $sessionStart = Get-Date
    $sessPath = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-monitor-session.json'
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
        & $reportScript
        Write-Host "[VPP chat] GREEN detected, stopping reporter" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds $IntervalSec
}
