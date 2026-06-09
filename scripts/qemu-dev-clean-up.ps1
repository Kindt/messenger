# Чистый dev-стенд: сброс дисков + QEMU up + KORUS_BUILD=1 через cloud-init.

param(
    [switch]$SkipQemuInstall,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Clean QEMU dev stand (fresh disks, full build via cloud-init KORUS_BUILD=1).

  .\scripts\qemu-dev-clean-up.ps1
  .\scripts\qemu-dev-clean-up.ps1 -SkipQemuInstall

Logs:
  deploy\qemu\run\*-serial.log
  guest: /var/log/korus-bootstrap.log (via plink)

Monitor: .\scripts\qemu-watch.ps1 -NewWindow
Stop:    .\scripts\qemu-down.ps1
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot

& (Join-Path $Root "scripts\qemu-down.ps1")

Write-Host "=== Clean disks + start VMs (KORUS_BUILD=1 in cloud-init) ===" -ForegroundColor Cyan
& (Join-Path $Root "scripts\qemu-up.ps1") -SkipQemuInstall:$SkipQemuInstall

Write-Host ""
Write-Host "Bootstrap is async (10-40 min for full build). Monitor:" -ForegroundColor Yellow
Write-Host "  .\scripts\qemu-watch.ps1 -NewWindow"
Write-Host "  curl.exe http://127.0.0.1:18080/api/v1/health"
