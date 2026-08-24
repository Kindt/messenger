#Requires -Version 5.1
param([switch]$Help)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$PidPath = Join-Path $RunDir 'cycle-unattended.pid'

if ($Help) {
    Write-Host 'Usage: .\scripts\Stop-KorusCycleUnattended.ps1 — stop background cycle + VPP lab lock cleanup'
    exit 0
}

if (Test-Path $PidPath) {
    $pidStr = (Get-Content $PidPath -Raw).Trim()
    if ($pidStr -match '^\d+$') {
        Stop-Process -Id ([int]$pidStr) -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped cycle PID $pidStr" -ForegroundColor Yellow
    }
    Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
}

& (Join-Path $Root 'scripts\vpp\Stop-VppLabRun.ps1') -Force
Write-Host '[OK] Cycle stop requested (VPP lab jobs stopped)' -ForegroundColor Green
