# Detect and terminate hung VPP lab processes (duplicate runners, stale plink).
param(
    [int]$MaxPlinkMin = 0,
    [switch]$Quiet,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root 'deploy\qemu\run'

if ($Help) {
    Write-Host 'Usage: Invoke-VppHungProcessCleanup.ps1 [-MaxPlinkMin 45] [-Quiet]'
    Write-Host 'Env: VPP_PLINK_MAX_MIN'
    exit 0
}

if ($MaxPlinkMin -le 0) {
    $MaxPlinkMin = 45
    if ($env:VPP_PLINK_MAX_MIN) {
        $n = 0
        if ([int]::TryParse($env:VPP_PLINK_MAX_MIN, [ref]$n) -and $n -gt 0) { $MaxPlinkMin = $n }
    }
}

. (Join-Path $Root 'deploy\qemu\lib\Invoke-KorusGuestRemoteJob.ps1')

$actions = @()
$lockPath = Join-Path $RunDir 'vpp-lab-run.lock'
$ownerPid = 0
if (Test-Path -LiteralPath $lockPath) {
    $line0 = Get-Content -LiteralPath $lockPath -TotalCount 1 -ErrorAction SilentlyContinue
    if ("$line0" -match '^\d+$') { $ownerPid = [int]$line0 }
}

Get-CimInstance Win32_Process -Filter "Name='powershell.exe' OR Name='pwsh.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*run-vpp-until-green*' -or $_.CommandLine -like '*Start-VppAfterStackRecovery*' } |
    ForEach-Object {
        if ($_.ProcessId -eq $PID) { return }
        if ($ownerPid -gt 0 -and $_.ProcessId -eq $ownerPid) { return }
        if (-not $Quiet) {
            Write-Host "[hung] duplicate VPP runner pid $($_.ProcessId)" -ForegroundColor Yellow
        }
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $actions += "dup-runner:$($_.ProcessId)"
    }

$plinkCutoff = (Get-Date).AddMinutes(-$MaxPlinkMin)
Get-CimInstance Win32_Process -Filter "Name='plink.exe'" -ErrorAction SilentlyContinue |
    ForEach-Object {
        $proc = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
        if (-not $proc) { return }
        if ($proc.StartTime -gt $plinkCutoff) { return }
        $cmd = "$($_.CommandLine)"
        if ($cmd -notmatch ':-P (12221|12222|12223)\b') { return }
        if (-not $Quiet) {
            Write-Host "[hung] plink pid $($_.ProcessId) age > ${MaxPlinkMin}m" -ForegroundColor Yellow
        }
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $actions += "plink:$($_.ProcessId)"
    }

$sessionPath = Join-Path $RunDir 'vpp-evidence\vpp-monitor-session.json'
if (Test-Path $sessionPath) {
    try {
        $session = Get-Content -Raw $sessionPath | ConvertFrom-Json
        foreach ($role in @('watcher_pid', 'reporter_pid')) {
            $wpid = [int]$session.$role
            if ($wpid -le 0) { continue }
            $alive = Get-Process -Id $wpid -ErrorAction SilentlyContinue
            if ($alive) { continue }
        }
        $watchers = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -like '*Start-VppStatusWatcher*' -or $_.CommandLine -like '*Start-VppChatReporter*' }
        if ($watchers.Count -gt 2) {
            $watchers | Select-Object -Skip 2 | ForEach-Object {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                $actions += "dup-watcher:$($_.ProcessId)"
            }
        }
    } catch { }
}

if ($actions.Count -and -not $Quiet) {
    Write-Host "[hung] cleaned $($actions.Count): $($actions -join ', ')" -ForegroundColor DarkYellow
}
return $actions
