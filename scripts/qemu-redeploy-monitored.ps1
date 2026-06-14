# Monitor-fix-restart loop for Korus QEMU redeploy (see .cursor/rules/qemu-redeploy-monitor.mdc).
param(
    [ValidateSet("server", "web", "both")]
    [string]$Target = "server",
    [switch]$EnableHotswap,
    [switch]$Rebuild,
    [switch]$Force,
    [int]$MaxCycles = 5,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Monitored QEMU redeploy: ensure VMs up, redeploy (sync default), detect hangs/VM death, retry.

  .\scripts\qemu-redeploy-monitored.ps1 -Target server
  .\scripts\qemu-redeploy-monitored.ps1 -Target both -EnableHotswap
  .\scripts\qemu-redeploy-monitored.ps1 -Target server -Rebuild

Logs: deploy\qemu\run\redeploy-monitored.log

"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$LogPath = Join-Path $RunDir "redeploy-monitored.log"
$GoldenLock = Join-Path $RunDir "golden-path.no-auto-restart"
$TcgStateFile = Join-Path $RunDir "redeploy-monitored-tcg.json"
$Lib = Join-Path $Root "deploy\qemu\lib"

. (Join-Path $Lib "Test-KorusQemuProcess.ps1")
. (Join-Path $Lib "Get-KorusQemuHostHealth.ps1")
. (Join-Path $Lib "Get-KorusGuestBootstrapPhase.ps1")
. (Join-Path $Lib "Update-KorusGuestRepo.ps1")

Set-Content -Path $GoldenLock -Value ((Get-Date).ToString("o")) -Encoding ascii
Write-Host "golden-path lock: skip auto-restart during monitored redeploy" -ForegroundColor DarkGray

$script:UseTcgFallback = $false
if (Test-Path $TcgStateFile) {
    try {
        $tcgState = Get-Content $TcgStateFile -Raw | ConvertFrom-Json
        $script:UseTcgFallback = [bool]$tcgState.useTcg
    } catch { }
}

function Write-MonLog {
    param([string]$Message, [string]$Color = "DarkGray")
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message"
    Add-Content -Path $LogPath -Value $line -Encoding ascii
    Write-Host $line -ForegroundColor $Color
}

function Set-MonTcgState {
    param([bool]$UseTcg)
    $script:UseTcgFallback = $UseTcg
    @{ useTcg = $UseTcg; at = (Get-Date).ToString("o") } | ConvertTo-Json | Set-Content -Path $TcgStateFile -Encoding ascii
}

function Clear-KorusRedeployLocks {
    foreach ($role in @("server", "web")) {
        $lock = Join-Path $RunDir "qemu-redeploy-$role.lock"
        if (Test-Path $lock) {
            $age = [math]::Round(((Get-Date) - (Get-Item $lock).LastWriteTime).TotalMinutes, 1)
            Remove-Item $lock -Force -ErrorAction SilentlyContinue
            Write-MonLog "cleared qemu-redeploy-$role.lock (age ${age}m)" "Yellow"
        }
    }
}

function Test-KorusSshReady {
    foreach ($port in @(12221, 12222)) {
        $r = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if (-not $r.TcpTestSucceeded) { return $false }
    }
    return $true
}

function Wait-KorusBootstrapReady {
    $deadline = (Get-Date).AddMinutes(15)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
            throw "VM died during bootstrap SSH wait"
        }
        if (Test-KorusSshReady) {
            Write-MonLog "SSH 12221/12222 ready; settling 90s before redeploy" "Green"
            Start-Sleep -Seconds 90
            if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
                throw "VM died during post-SSH settle"
            }
            return
        }
        Start-Sleep -Seconds 15
    }
    throw "guest SSH not ready within 15m (cloud-init?)"
}

function Ensure-KorusStackUp {
    param([datetime]$CycleStarted)
    $started = $false
    if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
        if ($script:UseTcgFallback) {
            $env:KORUS_QEMU_FORCE_TCG = '1'
            Write-MonLog "starting qemu-up -KeepDisks with KORUS_QEMU_FORCE_TCG=1" "Yellow"
        } else {
            Remove-Item Env:KORUS_QEMU_FORCE_TCG -ErrorAction SilentlyContinue
            Write-MonLog "no Korus QEMU VMs; starting qemu-up -KeepDisks" "Yellow"
        }
        & (Join-Path $Root "scripts\qemu-up.ps1") -KeepDisks
        $started = $true
        $deadline = (Get-Date).AddMinutes(8)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 15
            if (Test-KorusQemuStackRunning -RunDir $RunDir) { break }
        }
        if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
            throw "QEMU stack did not start within 8m"
        }
        Write-MonLog "QEMU stack running" "Green"
    }
    if ($started -or -not (Test-KorusSshReady)) {
        Wait-KorusBootstrapReady
    }
}

function Invoke-KorusRedeployStep {
    param([string]$Step)
    $redeployParams = @{}
    switch ($Step) {
        "server" { $redeployParams.ServerOnly = $true }
        "web"    { $redeployParams.WebOnly = $true }
    }
    if ($Rebuild) { $redeployParams.Rebuild = $true }
    if ($Force) { $redeployParams.Force = $true }
    $mode = if ($Rebuild) { "rebuild" } else { "sync" }
    $flagList = @($redeployParams.Keys | ForEach-Object { "-$_" }) -join ' '
    Write-MonLog "starting qemu-redeploy.ps1 $flagList mode=$mode" "Cyan"
    & (Join-Path $Root "scripts\qemu-redeploy.ps1") @redeployParams
}

function Test-TargetReady {
    param([string]$Step)
    switch ($Step) {
        "server" { return (Test-KorusHostApiReady) }
        "web"    { return (Test-KorusHostUiReady) }
        default  { return $false }
    }
}

function Write-GuestPhaseSnapshot {
    param([string]$Step)
    $serial = if ($Step -eq "server") { "server-serial.log" } else { "web-serial.log" }
    $port = if ($Step -eq "server") { 12221 } else { 12222 }
    $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir $serial) -Role $Step -SshPort $port
    if (-not $hk) { return }
    $tail = Get-KorusGuestBootstrapTail -HostKey $hk -Port $port
    $phase = Get-KorusGuestBootstrapPhase -BootstrapText $tail
    Write-MonLog "guest $Step phase=$phase" "DarkGray"
}

$steps = @()
switch ($Target) {
    "server" { $steps = @("server") }
    "web"    { $steps = @("web") }
    "both"   { $steps = @("server", "web") }
}

$modeLabel = if ($Rebuild) { "rebuild" } else { "sync" }
Write-MonLog "=== monitored redeploy target=$Target mode=$modeLabel maxCycles=$MaxCycles ===" "Cyan"

$cycle = 0
while ($cycle -lt $MaxCycles) {
    $cycle++
    $cycleStarted = Get-Date
    Write-MonLog "--- cycle $cycle/$MaxCycles ---" "Cyan"
    try {
        Ensure-KorusStackUp -CycleStarted $cycleStarted
        Clear-KorusRedeployLocks

        foreach ($step in $steps) {
            if (-not $Force -and (Test-TargetReady -Step $step)) {
                Write-MonLog "$step already ready on host; skip redeploy" "Green"
                continue
            }
            if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
                throw "VM died before $step redeploy"
            }
            Invoke-KorusRedeployStep -Step $step
            Write-GuestPhaseSnapshot -Step $step
            if (-not (Test-TargetReady -Step $step)) {
                throw "$step redeploy finished but host check failed"
            }
            Write-MonLog "$step redeploy OK" "Green"
        }

        if ($EnableHotswap) {
            Write-MonLog "enabling web hotswap" "Cyan"
            & (Join-Path $Root "scripts\qemu-web-hotswap.ps1") -Enable
        }

        Write-MonLog "=== monitored redeploy SUCCESS ===" "Green"
        Remove-Item $GoldenLock -Force -ErrorAction SilentlyContinue
        Remove-Item $TcgStateFile -Force -ErrorAction SilentlyContinue
        exit 0
    } catch {
        Write-MonLog "cycle $cycle failed: $($_.Exception.Message)" "Red"
        $vmDead = -not (Test-KorusQemuStackRunning -RunDir $RunDir)
        if ($vmDead) {
            $elapsedMin = ((Get-Date) - $cycleStarted).TotalMinutes
            if (-not $script:UseTcgFallback -and $elapsedMin -lt 15) {
                Set-MonTcgState -UseTcg $true
                Write-MonLog "VM died <15m; next cycle uses TCG fallback" "Yellow"
            }
            Write-MonLog "VMs down; will qemu-up on next cycle" "Yellow"
        }
        if ($cycle -ge $MaxCycles) {
            Write-MonLog "max cycles reached; see $LogPath" "Red"
            Remove-Item $GoldenLock -Force -ErrorAction SilentlyContinue
            throw
        }
        Start-Sleep -Seconds 10
    }
}
Remove-Item $GoldenLock -Force -ErrorAction SilentlyContinue
