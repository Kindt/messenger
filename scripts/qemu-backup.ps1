# Backup Korus QEMU overlay disks (server.qcow2, web.qcow2). VMs must be stopped.
param(
    [string]$Label = "",
    [switch]$Compress,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Backup QEMU guest disks (overlay qcow2 with Docker/stacks state).

  .\scripts\qemu-backup.ps1
  .\scripts\qemu-backup.ps1 -Label green-2026-06-12
  .\scripts\qemu-backup.ps1 -Compress   # qemu-img compressed copy (slow, smaller)

Requires: VMs stopped (.\scripts\qemu-down.ps1).
Output: deploy\qemu\backups\<timestamp>\server.qcow2, web.qcow2, manifest.json

Restore: .\scripts\qemu-restore.ps1 -From <backup-dir>

"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$ImagesDir = Join-Path $QemuRoot "images"
$BackupsRoot = Join-Path $QemuRoot "backups"
$RunDir = Join-Path $QemuRoot "run"

. (Join-Path $QemuRoot "config.ps1")
. (Join-Path $QemuRoot "lib\Test-KorusQemuProcess.ps1")

if (Test-KorusQemuStackRunning -RunDir $RunDir) {
    Write-Error "Korus QEMU VMs are running. Stop first: .\scripts\qemu-down.ps1"
}

$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$destName = if ($Label) { "${stamp}_$Label" } else { $stamp }
$dest = Join-Path $BackupsRoot $destName
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$qemuImg = Get-Command qemu-img -ErrorAction SilentlyContinue
if ($Compress -and -not $qemuImg) {
    Write-Error "qemu-img not found; install QEMU or omit -Compress"
}

$files = @()
foreach ($role in @("server", "web")) {
    $src = Join-Path $ImagesDir "$role.qcow2"
    if (-not (Test-Path $src)) {
        Write-Error "Missing disk: $src (start VMs at least once with qemu-up -KeepDisks)"
    }
    $out = Join-Path $dest "$role.qcow2"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    if ($Compress) {
        Write-Host "Converting $role.qcow2 (compressed)..." -ForegroundColor Cyan
        & $qemuImg.Source convert -p -c -O qcow2 $src $out
        if ($LASTEXITCODE -ne 0) { throw "qemu-img convert failed for $role" }
    } else {
        Write-Host "Copying $role.qcow2 ($([math]::Round((Get-Item $src).Length/1GB,2)) GB)..." -ForegroundColor Cyan
        Copy-Item -Path $src -Destination $out -Force
    }
    $sw.Stop()
    $gb = [math]::Round((Get-Item $out).Length / 1GB, 2)
    Write-Host "  [OK] $role.qcow2 ${gb} GB in $([math]::Round($sw.Elapsed.TotalSeconds,1))s" -ForegroundColor Green
    $files += @{
        name = "$role.qcow2"
        bytes = (Get-Item $out).Length
        compressed = [bool]$Compress
    }
}

$gitHead = ""
try {
    $gitHead = (git -C $Root rev-parse --short HEAD 2>$null).Trim()
} catch { }

$manifest = @{
    created_utc = (Get-Date).ToUniversalTime().ToString("o")
    label       = $Label
    git_head    = $gitHead
    files       = $files
    restore     = ".\scripts\qemu-restore.ps1 -From `"$dest`""
}
$manifestPath = Join-Path $dest "manifest.json"
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Path $manifestPath -Encoding utf8

Write-Host ""
Write-Host "[OK] Backup: $dest" -ForegroundColor Green
Write-Host "  manifest: $manifestPath" -ForegroundColor DarkGray
