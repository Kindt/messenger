# Download QEMU cloud images if missing. See deploy/qemu/images/README.md
param([switch]$Help)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if ($Help) {
    Write-Host @"
Ensure QEMU OS base images exist locally (not stored in git).

  .\scripts\ensure-qemu-images.ps1

Downloads ubuntu-24.04-minimal-cloudimg-amd64.img when absent.
Overlay disks (server.qcow2, web.qcow2) are created on first qemu-up.
"@
    exit 0
}

. (Join-Path $root "deploy\qemu\lib\Get-KorusCloudImage.ps1")
$path = Get-KorusCloudImage
Write-Host "[OK] Cloud image ready: $path" -ForegroundColor Green
