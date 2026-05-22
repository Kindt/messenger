# Установка QEMU в deploy/qemu/tools/qemu (Inno Setup /VERYSILENT) или через winget.
param(
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\qemu\install-qemu.ps1"
    exit 0
}

. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\Resolve-Qemu.ps1")

if (Resolve-KorusQemu) {
    Write-Host "[OK] QEMU already available: $(Resolve-KorusQemu)" -ForegroundColor Green
    exit 0
}

$dest = Join-Path $KorusQemuToolsDir "qemu"
New-Item -ItemType Directory -Force -Path $KorusQemuToolsDir | Out-Null

$installer = Join-Path $KorusQemuToolsDir "qemu-w64-setup.exe"
if (-not (Test-Path $installer)) {
    Write-Host "Downloading QEMU installer..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $KorusQemuInstallerUrl -OutFile $installer -UseBasicParsing
}

Write-Host "Installing QEMU to $dest (may prompt UAC)..." -ForegroundColor Cyan
$proc = Start-Process -FilePath $installer -ArgumentList @(
    "/VERYSILENT",
    "/SUPPRESSMSGBOXES",
    "/NORESTART",
    "/DIR=$dest"
) -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Warning "Installer exit $($proc.ExitCode). Trying winget..."
    winget install -e --id $KorusQemuWingetId --accept-package-agreements --accept-source-agreements
}

if (-not (Resolve-KorusQemu)) {
    Write-Error "QEMU not found after install. Install manually: winget install SoftwareFreedomConservancy.QEMU"
}
Write-Host "[OK] QEMU: $(Resolve-KorusQemu)" -ForegroundColor Green
