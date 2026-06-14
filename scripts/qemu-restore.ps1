# Restore Korus QEMU overlay disks from qemu-backup.ps1 output.
param(
    [Parameter(Mandatory)]
    [string]$From,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Restore QEMU guest disks from backup directory.

  .\scripts\qemu-restore.ps1 -From deploy\qemu\backups\2026-06-12_153045

Requires: VMs stopped. Then: .\scripts\qemu-up.ps1 -KeepDisks

"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$ImagesDir = Join-Path $QemuRoot "images"
$RunDir = Join-Path $QemuRoot "run"

if (-not [System.IO.Path]::IsPathRooted($From)) {
    $From = Join-Path $Root $From
}
if (-not (Test-Path $From)) {
    Write-Error "Backup not found: $From"
}

. (Join-Path $QemuRoot "lib\Test-KorusQemuProcess.ps1")
if (Test-KorusQemuStackRunning -RunDir $RunDir) {
    Write-Error "Stop VMs first: .\scripts\qemu-down.ps1"
}

foreach ($role in @("server", "web")) {
    $src = Join-Path $From "$role.qcow2"
    if (-not (Test-Path $src)) {
        Write-Error "Missing $src in backup"
    }
    $dest = Join-Path $ImagesDir "$role.qcow2"
    if (Test-Path $dest) {
        $bak = "$dest.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Host "Renaming current $role.qcow2 -> $(Split-Path $bak -Leaf)" -ForegroundColor Yellow
        Move-Item -Path $dest -Destination $bak -Force
    }
    Write-Host "Restoring $role.qcow2..." -ForegroundColor Cyan
    Copy-Item -Path $src -Destination $dest -Force
    Write-Host "  [OK] $dest" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] Disks restored. Start: .\scripts\qemu-up.ps1 -KeepDisks" -ForegroundColor Green
