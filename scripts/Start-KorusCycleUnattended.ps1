#Requires -Version 5.1
# Launch full unattended cycle in background (no IDE / chat polling required).
param(
    [ValidateSet('full', 'standard', 'quick')]
    [string]$VppLevel = 'full',
    [int]$MaxApiWaitMinutes = 90,
    [int]$MaxVppAttempts = 10,
    [switch]$SkipBuild,
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$Runner = Join-Path $Root 'scripts\run-korus-cycle-unattended.ps1'
$LogPath = Join-Path $RunDir 'cycle-unattended.log'
$PidPath = Join-Path $RunDir 'cycle-unattended.pid'

if ($Help) {
    Write-Host @"
Usage: .\scripts\Start-KorusCycleUnattended.ps1 [-VppLevel full] [-Force]

Starts run-korus-cycle-unattended.ps1 in a hidden PowerShell (survives IDE close).

Monitor:
  Get-Content deploy/qemu/run/cycle-unattended-status.json
  Get-Content deploy/qemu/run/cycle-unattended.log -Tail 40
  deploy/qemu/run/vpp-evidence/vpp-checkpoint.json

Stop: .\scripts\Stop-KorusCycleUnattended.ps1
"@
    exit 0
}

if ((Test-Path $PidPath) -and -not $Force) {
    $oldPid = (Get-Content $PidPath -Raw).Trim()
    if ($oldPid -match '^\d+$') {
        $proc = Get-Process -Id ([int]$oldPid) -ErrorAction SilentlyContinue
        if ($proc -and $proc.Path -like '*powershell*') {
            Write-Host "[FAIL] Cycle already running PID $oldPid. Use -Force or Stop-KorusCycleUnattended.ps1" -ForegroundColor Red
            exit 2
        }
    }
}

$argList = @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Runner,
    '-VppLevel', $VppLevel,
    '-MaxApiWaitMinutes', "$MaxApiWaitMinutes",
    '-MaxVppAttempts', "$MaxVppAttempts"
)
if ($SkipBuild) { $argList += '-SkipBuild' }

"=== cycle unattended launch $(Get-Date -Format o) ===" | Add-Content $LogPath
$proc = Start-Process -FilePath 'powershell.exe' -PassThru -WindowStyle Hidden -ArgumentList $argList
$proc.Id | Set-Content -Path $PidPath -Encoding ASCII

Write-Host "[OK] Unattended cycle started PID $($proc.Id)" -ForegroundColor Green
Write-Host "  status: deploy/qemu/run/cycle-unattended-status.json" -ForegroundColor DarkGray
Write-Host "  log:    deploy/qemu/run/cycle-unattended.log" -ForegroundColor DarkGray
exit 0
