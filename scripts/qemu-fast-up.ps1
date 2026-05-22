# Enable WHPX (if possible) and start QEMU VMs in fast mode.
param(
    [switch]$SkipEnable,
    [switch]$KeepDisks,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\qemu-fast-up.ps1 [-SkipEnable] [-KeepDisks]"
    Write-Host "  -SkipEnable  Skip enable-fast-mode (only restart VMs)"
    Write-Host "  -KeepDisks   Keep existing VM overlay disks (default: reset disks)"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$enableScript = Join-Path $Root "deploy\qemu\enable-fast-mode.ps1"
$qemuUp = Join-Path $Root "deploy\qemu\qemu-up.ps1"
$qemuDown = Join-Path $Root "deploy\qemu\qemu-down.ps1"

if (-not $SkipEnable) {
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
        IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

    if ($isAdmin) {
        & $enableScript
        $enableExit = $LASTEXITCODE
    } else {
        Write-Host "Requesting elevation to enable WHPX..." -ForegroundColor Cyan
        $proc = Start-Process -FilePath "powershell.exe" -Verb RunAs -Wait -PassThru -ArgumentList @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $enableScript
        )
        $enableExit = $proc.ExitCode
    }

    if ($enableExit -eq 1) {
        Write-Error "enable-fast-mode failed (exit $enableExit)"
    }
    if ($enableExit -eq 2) {
        Write-Host ""
        Write-Host "Reboot Windows, then run: .\scripts\qemu-fast-up.ps1 -SkipEnable" -ForegroundColor Yellow
        exit 2
    }
}

. (Join-Path $Root "deploy\qemu\lib\Test-KorusWhpx.ps1")
$accel = Test-KorusWhpxAvailable
Write-Host "Accelerator: $($accel.Mode.ToUpper()) - $($accel.Message)" -ForegroundColor $(if ($accel.Ok) { "Green" } else { "Yellow" })

& $qemuDown
$upArgs = @{}
if ($KeepDisks) { $upArgs["KeepDisks"] = $true }
& $qemuUp @upArgs

Write-Host ""
if ($accel.Ok) {
    Write-Host "[OK] VMs started in fast mode (WHPX)" -ForegroundColor Green
} else {
    Write-Host "[OK] VMs started (TCG fallback; run enable-fast-mode + reboot for WHPX)" -ForegroundColor Yellow
}
