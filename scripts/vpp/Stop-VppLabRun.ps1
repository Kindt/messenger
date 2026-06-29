# Stop all VPP lab orchestrators, watchers, and blocking guest plink sessions.
param(
    [switch]$Force,
    [switch]$KeepQemu,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root 'deploy\qemu\run'
$EvDir = Join-Path $RunDir 'vpp-evidence'

if ($Help) {
    Write-Host @'
Usage: .\scripts\vpp\Stop-VppLabRun.ps1 [-Force] [-KeepQemu]

Stops: run-vpp-until-green, Start-Vpp*, recovery scripts, chat/status watchers,
       integrations-gate-preflight wait loops, long-running plink on :12221-:12223.
Clears: vpp-lab-run.lock (with -Force also kills lock owner).
'@
    exit 0
}

. (Join-Path $Root 'deploy\qemu\lib\Invoke-KorusGuestRemoteJob.ps1')

$patterns = @(
    'run-vpp-until-green',
    'run-vpp-full',
    'Start-VppAfterStackRecovery',
    'Start-VppLabRun',
    'Start-VppMonitoredLabRun',
    'Start-VppChatReporter',
    'Start-VppStatusWatcher',
    'Start-VppRealtimeWatch',
    'integrations-gate-preflight',
    'Repair-IntegrationsGateway',
    'Wait-IntegrationsOnline'
)

$killed = @()
Get-CimInstance Win32_Process -Filter "Name='powershell.exe' OR Name='pwsh.exe'" -ErrorAction SilentlyContinue |
    ForEach-Object {
        $cmd = "$($_.CommandLine)"
        if ($cmd -match 'Stop-VppLabRun') { return }
        foreach ($p in $patterns) {
            if ($cmd -like "*$p*") {
                if ($_.ProcessId -eq $PID) { return }
                Write-Host "[stop] powershell pid $($_.ProcessId): $p" -ForegroundColor Yellow
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
                $killed += "ps:$($_.ProcessId):$p"
                break
            }
        }
    }

foreach ($port in @(12221, 12222, 12223)) {
    Stop-KorusGuestPlinkOnPort -Port $port
    $killed += "plink-cleared:$port"
}

$lockScript = Join-Path $Root 'scripts\vpp\Invoke-VppLabRunLock.ps1'
if (Test-Path $lockScript) {
    & $lockScript -Action Exit -Force
}

foreach ($guest in @('server', 'web', 'integrations')) {
    $lock = Join-Path $RunDir "guest-task-$guest.lock"
    if (Test-Path -LiteralPath $lock) {
        Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
        $killed += "guest-lock:$guest"
    }
}

$nowLocal = Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'
Write-Host "[stop] done at $nowLocal (killed $($killed.Count) actions)" -ForegroundColor Green
if ($killed.Count) {
    $killed | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}

if (-not (Test-Path $EvDir)) { exit 0 }
$tickLog = Join-Path $EvDir 'vpp-status-ticks.jsonl'
$evt = [ordered]@{
    tick_number = 0
    tick_at = (Get-Date).ToUniversalTime().ToString('o')
    tick_at_local = $nowLocal
    event = 'STOP_ALL'
    killed = @($killed)
}
Add-Content -Path $tickLog -Value ($evt | ConvertTo-Json -Compress) -Encoding utf8
