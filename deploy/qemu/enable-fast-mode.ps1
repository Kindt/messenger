# Enable Windows Hypervisor Platform for QEMU WHPX (fast mode).
# Requires Administrator. Reboot may be required before WHPX works.
param(
    [switch]$ProbeOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\deploy\qemu\enable-fast-mode.ps1 [-ProbeOnly]

Enables HypervisorPlatform (+ optional hypervisor launch type) and probes QEMU WHPX.

Exit codes:
  0  WHPX works now
  2  Changes applied; reboot required
  1  Failed or not elevated
"@
    exit 0
}

function Test-IsAdmin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p = [Security.Principal.WindowsPrincipal]$id
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

$lib = Join-Path $PSScriptRoot "lib"
. (Join-Path $lib "Test-KorusWhpx.ps1")

if (-not (Test-IsAdmin)) {
    Write-Host "Administrator rights required. Re-run elevated:" -ForegroundColor Yellow
    Write-Host "  Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"'" -ForegroundColor Cyan
    exit 1
}

if ($ProbeOnly) {
    $probe = Test-KorusWhpxAvailable
    Write-Host $probe.Message -ForegroundColor $(if ($probe.Ok) { "Green" } else { "Yellow" })
    exit $(if ($probe.Ok) { 0 } else { 2 })
}

$probeBefore = Test-KorusWhpxAvailable
if ($probeBefore.Ok) {
    Write-Host "[OK] Fast mode already active (WHPX)" -ForegroundColor Green
    exit 0
}

Write-Host "Enabling Windows Hypervisor Platform for QEMU WHPX..." -ForegroundColor Cyan

$changed = $false
$featureNames = @(
    "HypervisorPlatform",
    "VirtualMachinePlatform"
)

foreach ($name in $featureNames) {
    $state = (Get-WindowsOptionalFeature -Online -FeatureName $name -ErrorAction SilentlyContinue).State
    if ($state -eq "Enabled") {
        Write-Host "  $name : already Enabled" -ForegroundColor DarkGray
        continue
    }
    if ($state -eq "Disabled" -or $state -eq "EnablePending") {
        Write-Host "  Enabling $name ..." -ForegroundColor Cyan
        $r = Enable-WindowsOptionalFeature -Online -FeatureName $name -All -NoRestart
        if ($r.RestartNeeded) { $changed = $true }
    } elseif (-not $state) {
        Write-Host "  $name : not present on this edition (skip)" -ForegroundColor DarkGray
    }
}

try {
    $bcd = bcdedit /enum "{current}" 2>&1 | Out-String
    if ($bcd -notmatch "hypervisorlaunchtype\s+Auto") {
        Write-Host "  Setting hypervisorlaunchtype=auto (bcdedit)..." -ForegroundColor Cyan
        bcdedit /set hypervisorlaunchtype auto | Out-Null
        $changed = $true
    }
} catch {
    Write-Warning "bcdedit failed: $_"
}

$probeAfter = Test-KorusWhpxAvailable
if ($probeAfter.Ok) {
    Write-Host "[OK] WHPX is available - fast mode ready (no reboot needed)" -ForegroundColor Green
    exit 0
}

if ($changed) {
    Write-Host "[!] Hypervisor enabled; reboot Windows, then run:" -ForegroundColor Yellow
    Write-Host "    .\scripts\qemu-fast-up.ps1" -ForegroundColor Cyan
    exit 2
}

Write-Host "[--] WHPX still unavailable after enabling features." -ForegroundColor Yellow
Write-Host "    Check: BIOS virtualization (VT-x/AMD-V), conflicting hypervisors (VMware/VirtualBox)." -ForegroundColor DarkGray
Write-Host "    QEMU will continue with TCG (slow)." -ForegroundColor DarkGray
exit 2
