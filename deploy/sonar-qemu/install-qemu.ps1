# Install QEMU under deploy/sonar-qemu/tools/qemu (or winget).
param([switch]$Help)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\sonar-qemu\install-qemu.ps1"
    exit 0
}

. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\Resolve-Qemu.ps1")

if (Resolve-SonarQemu) {
    Write-Host "[OK] QEMU already available: $(Resolve-SonarQemu)" -ForegroundColor Green
    exit 0
}

$dest = Join-Path $SonarQemuToolsDir "qemu"
New-Item -ItemType Directory -Force -Path $SonarQemuToolsDir | Out-Null

$installer = Join-Path $SonarQemuToolsDir "qemu-w64-setup.exe"
if (-not (Test-Path $installer)) {
    Write-Host "Downloading QEMU installer..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $SonarQemuInstallerUrl -OutFile $installer -UseBasicParsing
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
    winget install -e --id $SonarQemuWingetId --accept-package-agreements --accept-source-agreements
}

if (-not (Resolve-SonarQemu)) {
    Write-Error "QEMU not found after install. Manual: winget install SoftwareFreedomConservancy.QEMU"
}
Write-Host "[OK] QEMU: $(Resolve-SonarQemu)" -ForegroundColor Green
