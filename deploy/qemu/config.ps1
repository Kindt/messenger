# Общие константы QEMU-стенда Korus
$script:KorusQemuDeployRoot = $PSScriptRoot
$script:KorusQemuRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:KorusQemuToolsDir = Join-Path $PSScriptRoot "tools"
$script:KorusQemuImagesDir = Join-Path $PSScriptRoot "images"
$script:KorusQemuRunDir = Join-Path $PSScriptRoot "run"
# HTTP snapshot of repo for guests (QEMU smb= at 10.0.2.4 is unreliable on Windows hosts)
$script:KorusQemuRepoHttpPort = 18890
# Guest resources — see deploy/qemu/RESOURCES.md for sizing breakdown
# Server: 14 containers (full-server); Web: 2x Tomcat + nginx
$script:KorusQemuServerMemoryMb = 10240
$script:KorusQemuServerSmp = 4
$script:KorusQemuWebMemoryMb = 3072
$script:KorusQemuWebSmp = 1
# Legacy alias (server); prefer role-specific settings in Start-KorusVm.ps1
$script:KorusQemuVmMemoryMb = $script:KorusQemuServerMemoryMb
$script:KorusQemuVmSmp = $script:KorusQemuServerSmp
$script:KorusQemuVmDiskGb = 40
$script:KorusQemuWebDiskGb = 24
$script:KorusQemuCloudDir = Join-Path $PSScriptRoot "cloud-init"

# L2 hub между ВМ на одном хосте (mcast на Windows часто недоступен)
$script:KorusQemuLanHubPort = 40176
$script:KorusQemuLanOctets = "192.168.76"
$script:KorusQemuServerIp = "192.168.76.10"
$script:KorusQemuWebIp = "192.168.76.20"

$script:KorusQemuCloudImageUrl = "https://cloud-images.ubuntu.com/releases/24.04/release/ubuntu-24.04-server-cloudimg-amd64.img"
$script:KorusQemuCloudImage = Join-Path $script:KorusQemuImagesDir "ubuntu-24.04-server-cloudimg-amd64.img"

$script:KorusQemuWingetId = "SoftwareFreedomConservancy.QEMU"
$script:KorusQemuInstallerUrl = "https://qemu.weilnetz.de/w64/2026/qemu-w64-setup-20260422.exe"

# Display: none (default) | gtk | sdl | default — set KORUS_QEMU_DISPLAY or qemu-up -Graphical
