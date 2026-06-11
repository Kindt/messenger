# Stop qemu-plan-orchestrator.ps1
param([switch]$Quiet, [switch]$Help)
$ErrorActionPreference = "SilentlyContinue"
$Root = Split-Path -Parent $PSScriptRoot
$PidPath = Join-Path $Root "deploy\qemu\run\plan-orchestrator.pid"

if ($Help) {
    Write-Host "Usage: .\scripts\stop-qemu-plan-orchestrator.ps1"
    exit 0
}

$stopped = @()
if (Test-Path $PidPath) {
    $pidVal = [int](Get-Content $PidPath -Raw).Trim()
    if (Get-Process -Id $pidVal -ErrorAction SilentlyContinue) {
        Stop-Process -Id $pidVal -Force -ErrorAction SilentlyContinue
        $stopped += $pidVal
    }
    Remove-Item $PidPath -Force -ErrorAction SilentlyContinue
}

Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.CommandLine -match 'qemu-plan-orchestrator\.ps1') {
        if ($_.ProcessId -notin $stopped) {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            $stopped += $_.ProcessId
        }
    }
}

if (-not $Quiet) {
    if ($stopped.Count -gt 0) {
        Write-Host "Stopped plan orchestrator PID(s): $($stopped -join ', ')" -ForegroundColor Green
    } else {
        Write-Host "No plan orchestrator running." -ForegroundColor DarkGray
    }
}
