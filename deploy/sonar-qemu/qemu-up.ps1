# Start single Ubuntu QEMU VM with Docker + SonarQube Community.
param(
    [switch]$InstallQemuOnly,
    [switch]$SkipQemuInstall,
    [switch]$KeepDisk,
    [switch]$Graphical,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\deploy\sonar-qemu\qemu-up.ps1 [-InstallQemuOnly] [-SkipQemuInstall] [-KeepDisk] [-Graphical]

Starts one Ubuntu 24.04 VM (WHPX/TCG), installs Docker via cloud-init, runs SonarQube Community.
UI on host: http://127.0.0.1:19000  (admin password from config / SONAR_QEMU_ADMIN_PASSWORD)
SSH: ssh sonar@127.0.0.1 -p 12224  (guest password from config / SONAR_QEMU_GUEST_PASSWORD)
"@
    exit 0
}

. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\Resolve-Qemu.ps1")
. (Join-Path $PSScriptRoot "lib\Get-CloudImage.ps1")
. (Join-Path $PSScriptRoot "lib\New-CidataIso.ps1")

if (-not $SkipQemuInstall -and -not (Resolve-SonarQemu)) {
    & (Join-Path $PSScriptRoot "install-qemu.ps1")
}
if ($InstallQemuOnly) { exit 0 }

$qemu = Resolve-SonarQemu
if (-not $qemu) { throw "QEMU required. Run: .\deploy\sonar-qemu\install-qemu.ps1" }

New-Item -ItemType Directory -Force -Path $SonarQemuRunDir | Out-Null

# Stop previous lab VM if still running
$pidFile = Join-Path $SonarQemuRunDir "sonar.pid"
if (Test-Path $pidFile) {
    $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($oldPid) {
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if (-not $KeepDisk -and (Test-Path $SonarQemuDisk)) {
    Write-Host "Removing previous VM disk (use -KeepDisk to preserve)..." -ForegroundColor DarkGray
    Remove-Item -Force $SonarQemuDisk
}

$base = Get-SonarCloudImage
$disk = New-SonarVmDisk -BaseImage $base
$seed = New-SonarCidataSeed

$serialLog = Join-Path $SonarQemuRunDir "sonar-serial.log"
$errLog = Join-Path $SonarQemuRunDir "sonar-qemu.err"
if (Test-Path $errLog) { Remove-Item -Force $errLog }

$hostFwd = @(
    "hostfwd=tcp:127.0.0.1:$($SonarQemuHttpHostPort)-:9000",
    "hostfwd=tcp:127.0.0.1:$($SonarQemuSshHostPort)-:22"
) -join ","

$accelArgs = @("-accel", "tcg")
try {
    $whpxProbe = & $qemu -accel help 2>&1 | Out-String
    if ($whpxProbe -match "whpx") {
        $accelArgs = @("-accel", "whpx,kernel-irqchip=off", "-cpu", "qemu64")
    }
} catch {
    # keep tcg
}

$displayArgs = if ($Graphical) { @("-display", "gtk") } else { @("-display", "none") }

$diskPath = ($disk -replace '\\', '/')
$seedPath = ($seed -replace '\\', '/')
$args = @(
    "-name", $SonarQemuVmName,
    "-machine", "pc",
    "-m", "$SonarQemuVmMemoryMb",
    "-smp", "$SonarQemuVmSmp"
) + $accelArgs + @(
    "-drive", "file=$diskPath,if=virtio,format=qcow2",
    "-drive", "file=$seedPath,format=raw,if=ide,media=cdrom,readonly=on",
    "-device", "virtio-rng-pci",
    "-netdev", "user,id=net0,$hostFwd",
    "-device", "virtio-net-pci,netdev=net0,mac=52:54:00:12:34:90",
    "-serial", "file:sonar-serial.log"
) + $displayArgs

Write-Host "Starting $SonarQemuVmName VM ($SonarQemuVmMemoryMb MB)..." -ForegroundColor Cyan
$proc = Start-Process -FilePath $qemu -ArgumentList $args -WorkingDirectory $SonarQemuRunDir `
    -PassThru -WindowStyle Hidden -RedirectStandardError $errLog
Start-Sleep -Seconds 4
if ($proc.HasExited) {
    $err = if (Test-Path $errLog) { Get-Content -Raw $errLog } else { "" }
    throw "QEMU exited immediately. $err"
}
Set-Content -Path $pidFile -Value $proc.Id -Encoding ascii

Write-Host ""
Write-Host "[OK] QEMU started (pid $($proc.Id))" -ForegroundColor Green
Write-Host "  UI:   $SonarQemuUrl"
Write-Host "  SSH:  ssh $($SonarQemuGuestUser)@127.0.0.1 -p $SonarQemuSshHostPort  (password from config/env)"
Write-Host "  Logs: $serialLog"
Write-Host "  First boot installs Docker + pulls Sonar images (10-30+ min)."
Write-Host "  Wait/scan: .\deploy\sonar-qemu\sonar-scan.ps1"
Write-Host "  Stop:      .\deploy\sonar-qemu\qemu-down.ps1"
