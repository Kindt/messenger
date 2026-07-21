# Shared constants for the SonarQube Community QEMU lab (single reusable VM).
# Copy deploy/sonar-qemu into any repo (or point -RepoRoot); project identity comes from sonar-project.properties.
#
# Secrets: prefer env vars or config.local.ps1 (gitignored). Defaults below are lab-only fall-backs.
# Policy: docs/plans/2026-07-15-r-secrets-policy.md — never commit real secrets.
# Caveat: cloud-init/user-data plain_text_passwd applies on first boot only (wipe disk to reseed).
$script:SonarQemuRoot = $PSScriptRoot
$script:SonarQemuRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:SonarQemuToolsDir = Join-Path $PSScriptRoot "tools"
$script:SonarQemuImagesDir = Join-Path $PSScriptRoot "images"
$script:SonarQemuRunDir = Join-Path $PSScriptRoot "run"
$script:SonarQemuCloudDir = Join-Path $PSScriptRoot "cloud-init"

# QEMU -name; also matched by qemu-down (plus legacy dqlclient-sonar)
$script:SonarQemuVmName = "sonar-lab"

$script:SonarQemuVmMemoryMb = 6144
$script:SonarQemuVmSmp = 2
$script:SonarQemuVmDiskGb = 24
$script:SonarQemuSshHostPort = 12224
$script:SonarQemuHttpHostPort = 19000
$script:SonarQemuGuestUser = "sonar"

$script:SonarQemuCloudImageUrl = "https://cloud-images.ubuntu.com/releases/24.04/release/ubuntu-24.04-server-cloudimg-amd64.img"
$script:SonarQemuCloudImage = Join-Path $script:SonarQemuImagesDir "ubuntu-24.04-server-cloudimg-amd64.img"
$script:SonarQemuDisk = Join-Path $script:SonarQemuImagesDir "sonar-vm.qcow2"

$script:SonarQemuWingetId = "SoftwareFreedomConservancy.QEMU"
$script:SonarQemuInstallerUrl = "https://qemu.weilnetz.de/w64/2026/qemu-w64-setup-20260422.exe"

$script:SonarQemuUrl = "http://127.0.0.1:$($script:SonarQemuHttpHostPort)"
$script:SonarQemuAdminUser = "admin"

# Pinned scanner image (avoid floating :latest).
$script:SonarQemuScannerImage = "sonarsource/sonar-scanner-cli:12.1.0.3233_8.0.1"

# Lab defaults — override via env or config.local.ps1 (do not commit real secrets).
$script:SonarQemuGuestPassword = if ($env:SONAR_QEMU_GUEST_PASSWORD) { $env:SONAR_QEMU_GUEST_PASSWORD } else { "sonar" }
$script:SonarQemuAdminPassword = if ($env:SONAR_QEMU_ADMIN_PASSWORD) { $env:SONAR_QEMU_ADMIN_PASSWORD } else { "AdminChangeMe1!" }
$script:SonarQemuDbPassword = if ($env:SONAR_QEMU_DB_PASSWORD) { $env:SONAR_QEMU_DB_PASSWORD } else { "sonar" }

$localConfig = Join-Path $PSScriptRoot "config.local.ps1"
if (Test-Path -LiteralPath $localConfig) {
    . $localConfig
}
