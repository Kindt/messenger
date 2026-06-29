# Single active VPP lab orchestrator lock (host-side). Prevents parallel until-green / recovery jobs.
param(
    [ValidateSet('Enter', 'Exit', 'Test')]
    [string]$Action = 'Test',
    [string]$RunDir = '',
    [string]$TaskName = 'vpp-lab',
    [int]$MaxAgeMin = 240,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
if (-not $RunDir) {
    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $RunDir = Join-Path $Root 'deploy\qemu\run'
}
$lockPath = Join-Path $RunDir 'vpp-lab-run.lock'

function Read-Lock {
    if (-not (Test-Path -LiteralPath $lockPath)) { return $null }
    $lines = @(Get-Content -LiteralPath $lockPath -ErrorAction SilentlyContinue)
    $lockPid = 0
    if ($lines.Count -ge 1 -and "$($lines[0])" -match '^\d+$') { $lockPid = [int]$lines[0] }
    $task = if ($lines.Count -ge 2) { "$($lines[1])".Trim() } else { '' }
    $started = if ($lines.Count -ge 3) { "$($lines[2])".Trim() } else { '' }
    return [PSCustomObject]@{ ProcessId = $lockPid; TaskName = $task; Started = $started; Path = $lockPath }
}

function Clear-StaleLock {
    if (-not (Test-Path -LiteralPath $lockPath)) { return $false }
    $info = Read-Lock
    $ageMin = ((Get-Date) - (Get-Item -LiteralPath $lockPath).LastWriteTime).TotalMinutes
    $alive = $false
    if ($info.ProcessId -gt 0) {
        $alive = $null -ne (Get-Process -Id $info.ProcessId -ErrorAction SilentlyContinue)
    }
    if ($alive) { return $false }
    if ($ageMin -lt $MaxAgeMin -and $info.ProcessId -gt 0) { return $false }
    Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
    Write-Host "[VPP lock] cleared stale lock (task=$($info.TaskName) age=$([math]::Round($ageMin,1))m pid=$($info.ProcessId))" -ForegroundColor Yellow
    return $true
}

switch ($Action) {
    'Test' {
        Clear-StaleLock | Out-Null
        if (-not (Test-Path -LiteralPath $lockPath)) { exit 0 }
        $info = Read-Lock
        Write-Host "[VPP lock] active: pid=$($info.ProcessId) task=$($info.TaskName) since=$($info.Started)"
        exit 1
    }
    'Enter' {
        Clear-StaleLock | Out-Null
        if (Test-Path -LiteralPath $lockPath) {
            $info = Read-Lock
            if ($info.ProcessId -eq $PID) { exit 0 }
            if (-not $Force) {
                Write-Host "[FAIL] VPP lab already running (pid=$($info.ProcessId) task=$($info.TaskName)). Use -Force or stop the other job." -ForegroundColor Red
                exit 2
            }
            Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
        }
        @(
            $PID
            $TaskName
            (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
        ) | Set-Content -LiteralPath $lockPath -Encoding ASCII
        exit 0
    }
    'Exit' {
        if (Test-Path -LiteralPath $lockPath) {
            $info = Read-Lock
            if ($info.ProcessId -eq $PID -or $Force) {
                Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
            }
        }
        exit 0
    }
}
