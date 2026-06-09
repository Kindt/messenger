# QEMU dev stack watchdog - all runtime inside VMs (see .cursor/rules/qemu-host-isolation.mdc).
# Polls host-forwarded ports 18080/19088; starts or redeploys via qemu-up / qemu-redeploy.
param(
    [switch]$KeepDisks,
    [switch]$RedeployOnly,
    [switch]$Graphical,
    [ValidateSet("", "none", "gtk", "sdl", "default")]
    [string]$Display = "",
    [int]$MaxWaitMinutes = 25,
    [int]$MaxRedeployAttempts = 2,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$RunDir = Join-Path $QemuRoot "run"
$env:KORUS_DEBUG_SESSION = "6eddca"

. (Join-Path $QemuRoot "lib\Resolve-Qemu.ps1")
. (Join-Path $QemuRoot "lib\Write-KorusDebugLog.ps1")
Write-KorusDebugLog -Location "dev-up-watchdog.ps1:entry" -Message "watchdog begin" -HypothesisId "ALL" -Data @{
    KeepDisks = [bool]$KeepDisks; Graphical = [bool]$Graphical; Display = $Display
}

$ApiUrl = "http://127.0.0.1:18080"
$WebUrl = "http://127.0.0.1:19088/"

function Get-QemuUpArgs {
    $a = @{}
    if ($KeepDisks) { $a["KeepDisks"] = $true }
    if ($Graphical -and -not $Display) { $a["Graphical"] = $true }
    if ($Display) { $a["Display"] = $Display }
    return $a
}

if ($Help) {
    Write-Host @"
Usage: .\scripts\dev-up-watchdog.ps1 [-KeepDisks] [-RedeployOnly] [-Graphical] [-Display gtk|none] [-MaxWaitMinutes 25]

QEMU-only dev stack (no host Docker). Use -Graphical for GTK VM windows.
"@
    exit 0
}

function Test-QemuVmPid([string]$Role) {
    $pidFile = Join-Path $RunDir "$Role.pid"
    if (-not (Test-Path $pidFile)) { return $false }
    $raw = (Get-Content $pidFile -Raw).Trim()
    if ($raw -notmatch '^\d+$') { return $false }
    return [bool](Get-Process -Id ([int]$raw) -ErrorAction SilentlyContinue)
}

function Test-CoreApiHealth([string]$BaseUrl) {
    try {
        $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 10
        return @{ Ok = ($r.StatusCode -eq 200); Status = $r.StatusCode; Body = $r.Content.Substring(0, [Math]::Min(120, $r.Content.Length)) }
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Ok = $false; Status = $code; Error = $_.Exception.Message }
    }
}

function Test-WebUi([string]$Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10
        return @{ Ok = ($r.StatusCode -eq 200); Status = $r.StatusCode }
    } catch {
        return @{ Ok = $false; Error = $_.Exception.Message }
    }
}

function Wait-SshPortsReady {
    param([int]$MaxSeconds = 120)
    $deadline = (Get-Date).AddSeconds($MaxSeconds)
    while ((Get-Date) -lt $deadline) {
        $s = Test-NetConnection -ComputerName 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        $w = Test-NetConnection -ComputerName 127.0.0.1 -Port 12222 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if ($s.TcpTestSucceeded -and $w.TcpTestSucceeded) { return $true }
        Start-Sleep -Seconds 5
    }
    return $false
}

function Invoke-QemuRedeployIfNeeded {
    param([string]$Reason)
    Write-Host "Triggering qemu-redeploy ($Reason)..." -ForegroundColor Yellow
    if (-not (Wait-SshPortsReady)) {
        return $false
    }
    try {
        & (Join-Path $Root "scripts\qemu-redeploy.ps1")
        if ($LASTEXITCODE -ne 0) {
            return $false
        }
        return $true
    } catch {
        return $false
    }
}

function Test-QemuVmsAlive {
    $server = Test-QemuVmPid "server"
    $web = Test-QemuVmPid "web"
    return @{ Server = $server; Web = $web; All = ($server -and $web) }
}

function Wait-StackReady {
    param([int]$Minutes)
    $deadline = (Get-Date).AddMinutes($Minutes)
    $coreOk = $false
    $webOk = $false
    $readyOk = $false
    while ((Get-Date) -lt $deadline) {
        $vm = Test-QemuVmsAlive
        if (-not $vm.All) {
            return @{ Core = $coreOk; Web = $webOk; Ready = $readyOk; VmsDead = $true }
        }
        $h = Test-CoreApiHealth $ApiUrl
        if ($h.Ok) { $coreOk = $true }
        $w = Test-WebUi $WebUrl
        if ($w.Ok) { $webOk = $true }
        if ($coreOk) {
            try {
                $rd = Invoke-RestMethod -Uri "$ApiUrl/api/v1/health/ready" -TimeoutSec 10
                $readyOk = [bool]$rd.database_ok
            } catch { $readyOk = $false }
        }
        if ($coreOk -and $webOk -and $readyOk) {
            Write-KorusDebugLog -Location "dev-up-watchdog.ps1:ready" -Message "stack ready" -HypothesisId "H5" -Data @{ core = $coreOk; web = $webOk; ready = $readyOk }
            return @{ Core = $true; Web = $true; Ready = $true; VmsDead = $false }
        }
        Write-KorusDebugLog -Location "dev-up-watchdog.ps1:poll" -Message "health poll" -HypothesisId "H5" -Data @{
            core = $coreOk; web = $webOk; ready = $readyOk; vmServer = $vm.Server; vmWeb = $vm.Web
            coreStatus = $h.Status; coreErr = $h.Error
        }
        Write-Host "  waiting core=$coreOk web=$webOk ready=$readyOk ..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 15
    }
    return @{ Core = $coreOk; Web = $webOk; Ready = $readyOk; VmsDead = $false }
}

# H1: QEMU
if (-not (Resolve-KorusQemu)) {
    Write-Host "[FAIL H1] QEMU not installed. Run: .\deploy\qemu\install-qemu.ps1" -ForegroundColor Red
    exit 2
}

$serverUp = Test-QemuVmPid "server"
$webUp = Test-QemuVmPid "web"

if (-not $RedeployOnly -and (-not $serverUp -or -not $webUp)) {
    Write-Host "Starting QEMU VMs (KeepDisks=$KeepDisks)..." -ForegroundColor Cyan
    $upArgs = Get-QemuUpArgs
    Write-KorusDebugLog -Location "dev-up-watchdog.ps1:qemu-up" -Message "splat args" -HypothesisId "H0" -Data @{ upArgs = ($upArgs | ConvertTo-Json -Compress) }
    & (Join-Path $Root "scripts\qemu-up.ps1") @upArgs
    if ($LASTEXITCODE -ne 0) {
        exit 1
    }
    if ($KeepDisks) {
        Invoke-QemuRedeployIfNeeded -Reason "KeepDisks boot skips cloud-init runcmd" | Out-Null
    }
} elseif ($RedeployOnly -and (-not $serverUp -or -not $webUp)) {
    Write-Host "[FAIL H2] -RedeployOnly but VMs not running. Run without -RedeployOnly." -ForegroundColor Red
    exit 1
}

$redeployAttempt = 0
while ($true) {
    $state = Wait-StackReady -Minutes $MaxWaitMinutes
    if ($state.VmsDead) {
        Write-Host "[WARN H6] QEMU VM process exited - restarting VMs (KeepDisks)..." -ForegroundColor Yellow
        $restartArgs = Get-QemuUpArgs
        $restartArgs["KeepDisks"] = $true
        & (Join-Path $Root "scripts\qemu-up.ps1") @restartArgs
        if ($LASTEXITCODE -ne 0) {
            exit 1
        }
        Invoke-QemuRedeployIfNeeded -Reason "VM process restart with KeepDisks" | Out-Null
        continue
    }

    if ($state.Core -and $state.Web -and $state.Ready) {
        try {
            & (Join-Path $PSScriptRoot "smoke-ready.ps1") -BaseUrl $ApiUrl
            Write-Host "`n[OK] QEMU dev stack ready" -ForegroundColor Green
            Write-Host "  API:  $ApiUrl/api/v1/health" -ForegroundColor Green
            Write-Host "  UI:   $WebUrl" -ForegroundColor Green
            Write-Host "  Watch: .\scripts\qemu-watch.ps1 -Once" -ForegroundColor DarkGray
            exit 0
        } catch {
            # smoke-ready failed; fall through to redeploy
        }
    }

    $redeployAttempt++
    if ($redeployAttempt -gt $MaxRedeployAttempts) {
        Write-Host "[FAIL] Stack not ready (core=$($state.Core) web=$($state.Web) ready=$($state.Ready)). See deploy\qemu\run\*-serial.log" -ForegroundColor Red
        exit 1
    }

    Write-Host "[WARN H3/H4/H5] Bootstrap incomplete - qemu-redeploy attempt $redeployAttempt/$MaxRedeployAttempts ..." -ForegroundColor Yellow
    & (Join-Path $Root "scripts\qemu-redeploy.ps1")
}
