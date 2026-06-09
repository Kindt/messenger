# Poll QEMU stack every minute; optional redeploy when SSH is up but API is not.
param(
    [int]$MaxMinutes = 50,
    [int]$MinMinutesBeforeRedeploy = 15,
    [switch]$RedeployWhenSshUp,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
$ApiUrl = "http://127.0.0.1:18080"
$WebUrl = "http://127.0.0.1:19088/"
$Root = Split-Path -Parent $PSScriptRoot

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-stack-wait.ps1 [-MaxMinutes 50] [-RedeployWhenSshUp]"
    exit 0
}

function Test-Ssh($port) {
    $r = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    return [bool]$r.TcpTestSucceeded
}

function Test-Stack {
    $core = $false; $web = $false; $ready = $false
    try {
        $h = Invoke-WebRequest -Uri "$ApiUrl/api/v1/health" -UseBasicParsing -TimeoutSec 10
        $core = ($h.StatusCode -eq 200)
    } catch {}
    try {
        $w = Invoke-WebRequest -Uri $WebUrl -UseBasicParsing -TimeoutSec 10
        $web = ($w.StatusCode -eq 200)
    } catch {}
    if ($core) {
        try {
            $rd = Invoke-RestMethod -Uri "$ApiUrl/api/v1/health/ready" -TimeoutSec 10
            $ready = [bool]$rd.database_ok
        } catch {}
    }
    return @{ Core = $core; Web = $web; Ready = $ready }
}

$redeployed = $false
$deadline = (Get-Date).AddMinutes($MaxMinutes)
$minute = 0
while ((Get-Date) -lt $deadline) {
    $minute++
    $qemu = [bool](Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)
    $ssh = (Test-Ssh 12221) -and (Test-Ssh 12222)
    $s = Test-Stack
    $ts = Get-Date -Format "HH:mm:ss"
    Write-Host "[$ts] min=$minute qemu=$qemu ssh=$ssh core=$($s.Core) web=$($s.Web) ready=$($s.Ready)"
    if ($s.Core -and $s.Web -and $s.Ready) {
        Write-Host "[OK] Stack ready" -ForegroundColor Green
        exit 0
    }
    if ($RedeployWhenSshUp -and -not $redeployed -and $minute -ge $MinMinutesBeforeRedeploy -and $ssh -and -not $s.Core) {
        Write-Host "[$ts] SSH up, API down - running qemu-redeploy..." -ForegroundColor Yellow
        & (Join-Path $Root "scripts\qemu-redeploy.ps1")
        if ($LASTEXITCODE -eq 0) { $redeployed = $true }
    }
    if (-not $qemu) {
        Write-Host "[FAIL] QEMU process exited" -ForegroundColor Red
        exit 2
    }
    Start-Sleep -Seconds 60
}
Write-Host "[FAIL] Timeout after $MaxMinutes minutes" -ForegroundColor Red
exit 1
