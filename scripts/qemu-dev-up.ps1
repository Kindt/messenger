# Dev start: graphical QEMU + live monitor (+ optional watchdog).
param(
    [switch]$KeepDisks,
    [switch]$Headless,
    [ValidateSet("", "gtk", "sdl", "default")]
    [string]$Display = "",
    [switch]$NoWatch,
    [switch]$WatchOnly,
    [switch]$WithWatchdog,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$env:KORUS_DEBUG_SESSION = "6eddca"
. (Join-Path $Root "deploy\qemu\lib\Write-KorusDebugLog.ps1")
Write-KorusDebugLog -Location "qemu-dev-up.ps1:entry" -Message "dev-up begin" -HypothesisId "ALL" -Data @{
    KeepDisks = [bool]$KeepDisks; Headless = [bool]$Headless; Display = $Display
    WithWatchdog = [bool]$WithWatchdog; WatchOnly = [bool]$WatchOnly
}

if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-dev-up.ps1 [-KeepDisks] [-Headless] [-Display gtk|sdl|default] [-WithWatchdog] [-NoWatch] [-WatchOnly]

Default: GTK windows for both VMs + qemu-watch in a new PowerShell window.

  .\scripts\qemu-dev-up.ps1                    # graphical up + monitor
  .\scripts\qemu-dev-up.ps1 -KeepDisks         # reuse disks, redeploy via watchdog path
  .\scripts\qemu-dev-up.ps1 -WithWatchdog       # dev-up-watchdog instead of qemu-up only
  .\scripts\qemu-dev-up.ps1 -WatchOnly          # open monitor only (VMs already running)
  .\scripts\qemu-dev-up.ps1 -Headless           # no GTK (same as -Display none)

Also:
  .\scripts\qemu-logs.ps1 -Follow               # tail server serial
  .\scripts\qemu-down.ps1                       # stop all VMs
"@
    exit 0
}

if ($WatchOnly) {
    & (Join-Path $Root "scripts\qemu-watch.ps1") -NewWindow
    exit 0
}

$upArgs = @{}
if ($KeepDisks) { $upArgs["KeepDisks"] = $true }
if ($Headless) {
    $upArgs["Display"] = "none"
} elseif ($Display) {
    $upArgs["Display"] = $Display
} else {
    $upArgs["Graphical"] = $true
}

if ($WithWatchdog) {
    $wdArgs = @{}
    if ($KeepDisks) { $wdArgs["KeepDisks"] = $true }
    if ($Headless) {
        $wdArgs["Display"] = "none"
    } elseif ($Display) {
        $wdArgs["Display"] = $Display
    } else {
        $wdArgs["Graphical"] = $true
    }
    & (Join-Path $Root "scripts\dev-up-watchdog.ps1") @wdArgs
    exit $LASTEXITCODE
}

Write-Host "=== Korus dev up (QEMU + monitor) ===" -ForegroundColor Cyan
& (Join-Path $Root "scripts\qemu-up.ps1") @upArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $NoWatch) {
    & (Join-Path $Root "scripts\qemu-watch.ps1") -NewWindow
}

Write-Host ""
Write-Host "VM windows: live bootstrap on VGA (Ansible/Docker); if login prompt: .\scripts\qemu-console-on.ps1" -ForegroundColor Yellow
Write-Host "Bootstrap log on guest: /var/log/korus-bootstrap.log" -ForegroundColor DarkGray
Write-Host "API when ready: http://127.0.0.1:18080/api/v1/health" -ForegroundColor DarkGray
Write-Host "UI when ready:  http://127.0.0.1:19088/" -ForegroundColor DarkGray
