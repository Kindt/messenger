# Monitor-fix-restart loop for Korus QEMU redeploy (see .cursor/rules/qemu-redeploy-monitor.mdc).
param(
    [ValidateSet("server", "web", "both")]
    [string]$Target = "server",
    [switch]$EnableHotswap,
    [int]$MaxCycles = 5,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Monitored QEMU redeploy: ensure VMs up, redeploy, detect hangs/VM death, retry.

  .\scripts\qemu-redeploy-monitored.ps1 -Target server
  .\scripts\qemu-redeploy-monitored.ps1 -Target both -EnableHotswap

Logs: deploy\qemu\run\redeploy-monitored.log

"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$LogPath = Join-Path $RunDir "redeploy-monitored.log"
$Lib = Join-Path $Root "deploy\qemu\lib"

. (Join-Path $Lib "Test-KorusQemuProcess.ps1")

function Write-MonLog {
    param([string]$Message, [string]$Color = "DarkGray")
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message"
    Add-Content -Path $LogPath -Value $line -Encoding ascii
    Write-Host $line -ForegroundColor $Color
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

function Test-KorusHostApiReady {
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}

function Test-KorusHostUiReady {
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:19088/" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}

function Ensure-KorusStackUp {
    if (Test-KorusQemuStackRunning -RunDir $RunDir) { return }
    Write-MonLog "no Korus QEMU VMs; starting qemu-up -KeepDisks" "Yellow"
    & (Join-Path $Root "scripts\qemu-up.ps1") -KeepDisks
    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 15
        if (Test-KorusQemuStackRunning -RunDir $RunDir) {
            Write-MonLog "QEMU stack running" "Green"
            return
        }
    }
    throw "QEMU stack did not start within 8m"
}

function Invoke-KorusRedeployStep {
    param([string]$Step)
    $args = @()
    switch ($Step) {
        "server" { $args += "-ServerOnly" }
        "web"    { $args += "-WebOnly" }
    }
    Write-MonLog "starting qemu-redeploy.ps1 $($args -join ' ')" "Cyan"
    & (Join-Path $Root "scripts\qemu-redeploy.ps1") @args
}

function Test-TargetReady {
    param([string]$Step)
    switch ($Step) {
        "server" { return (Test-KorusHostApiReady) }
        "web"    { return (Test-KorusHostUiReady) }
        default  { return $false }
    }
}

$steps = @()
switch ($Target) {
    "server" { $steps = @("server") }
    "web"    { $steps = @("web") }
    "both"   { $steps = @("server", "web") }
}

Write-MonLog "=== monitored redeploy target=$Target maxCycles=$MaxCycles ===" "Cyan"

$cycle = 0
while ($cycle -lt $MaxCycles) {
    $cycle++
    Write-MonLog "--- cycle $cycle/$MaxCycles ---" "Cyan"
    try {
        Ensure-KorusStackUp
        Clear-KorusRedeployLocks

        foreach ($step in $steps) {
            if (Test-TargetReady -Step $step) {
                Write-MonLog "$step already ready on host; skip redeploy" "Green"
                continue
            }
            if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
                throw "VM died before $step redeploy"
            }
            Invoke-KorusRedeployStep -Step $step
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
        exit 0
    } catch {
        Write-MonLog "cycle $cycle failed: $($_.Exception.Message)" "Red"
        if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
            Write-MonLog "VMs down; will qemu-up on next cycle" "Yellow"
        }
        if ($cycle -ge $MaxCycles) {
            Write-MonLog "max cycles reached; see $LogPath and deploy\qemu\run\*-serial.log" "Red"
            throw
        }
        Start-Sleep -Seconds 10
    }
}
