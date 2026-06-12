# Fast webui sync to QEMU web guest (~5-15s). Requires hotswap or overlay path on guest.
param(
    [switch]$SkipTailwind,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-web-sync.ps1 [-SkipTailwind]

Packs modules/web-client/.../webui -> deploy/qemu/run/webui.tgz (~KiB, not 180 MiB repo.tgz).
Guest extracts to /mnt/korus/.../webui. With hotswap enabled, refresh browser (no container restart).

Prereq: qemu-up, web guest SSH; hotswap enabled via .\scripts\qemu-web-hotswap.ps1 -Enable
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$RunDir = Join-Path $QemuRoot "run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"

. (Join-Path $QemuRoot "lib\Test-KorusQemuProcess.ps1")
. (Join-Path $QemuRoot "lib\Sync-KorusGuestWebui.ps1")
. (Join-Path $QemuRoot "lib\Update-KorusGuestRepo.ps1")

if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Write-Error "QEMU not running. Start: .\scripts\qemu-up.ps1 -KeepDisks"
}

$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
if (-not $hk) { throw "web SSH host key not ready (see deploy/qemu/run/web-serial.log)" }

Write-Host "=== webui sync $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
$sw = [System.Diagnostics.Stopwatch]::StartNew()
Sync-KorusGuestWebui -SshPort 12222 -HostKey $hk -Plink $Plink -SkipTailwind:$SkipTailwind | Out-Null
$sw.Stop()
Write-Host "[OK] webui synced in $([math]::Round($sw.Elapsed.TotalSeconds, 1))s — refresh http://127.0.0.1:19088/" -ForegroundColor Green
