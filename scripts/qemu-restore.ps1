# Restore Korus QEMU overlay disks from qemu-backup.ps1 output.
param(
    [Parameter(Mandatory)]
    [string]$From,
    [ValidateSet("", "dev", "full")]
    [string]$StackProfile = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Restore QEMU guest disks from backup directory.

  .\scripts\qemu-restore.ps1 -From deploy\qemu\backups\2026-06-12_153045

Requires: VMs stopped. Then: .\scripts\qemu-up.ps1 -KeepDisks -StackProfile <dev|full>

  -StackProfile dev|full   default: from manifest.json or active profile

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
. (Join-Path $QemuRoot "lib\Get-KorusQemuStackProfile.ps1")
if (Test-KorusQemuStackRunning -RunDir $RunDir) {
    Write-Error "Stop VMs first: .\scripts\qemu-down.ps1"
}

$manifestPath = Join-Path $From "manifest.json"
if (-not $StackProfile -and (Test-Path $manifestPath)) {
    try {
        $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
        if ($manifest.stackProfile) { $StackProfile = $manifest.stackProfile }
    } catch {}
}
if (-not $StackProfile) { $StackProfile = Get-KorusQemuStackProfile }
Write-Host "Restore stack profile: $StackProfile" -ForegroundColor DarkGray

foreach ($role in @("server", "web")) {
    $diskName = Get-KorusVmDiskName -Role $role -StackProfile $StackProfile
    $src = Join-Path $From "$diskName.qcow2"
    if (-not (Test-Path $src)) {
        $legacy = Join-Path $From "$role.qcow2"
        if (Test-Path $legacy) { $src = $legacy } else { Write-Error "Missing $diskName.qcow2 (and legacy $role.qcow2) in backup" }
    }
    $dest = Join-Path $ImagesDir "$diskName.qcow2"
    if (Test-Path $dest) {
        $bak = "$dest.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Host "Renaming current $diskName.qcow2 -> $(Split-Path $bak -Leaf)" -ForegroundColor Yellow
        Move-Item -Path $dest -Destination $bak -Force
    }
    Write-Host "Restoring $diskName.qcow2..." -ForegroundColor Cyan
    Copy-Item -Path $src -Destination $dest -Force
    Write-Host "  [OK] $dest" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] Disks restored. Start: .\scripts\qemu-up.ps1 -KeepDisks -StackProfile $StackProfile" -ForegroundColor Green
