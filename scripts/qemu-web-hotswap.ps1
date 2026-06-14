# Enable QEMU web guest hot-swap (bind-mount repo webui). One-time after image exists.
param(
    [switch]$Enable,
    [switch]$SyncOnly,
    [switch]$Status,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage:
  .\scripts\qemu-web-hotswap.ps1 -Enable     # sync webui + switch guest to hotswap compose (~1-3 min)
  .\scripts\qemu-web-hotswap.ps1 -SyncOnly   # same as qemu-web-sync.ps1
  .\scripts\qemu-web-hotswap.ps1 -Status     # hotswap active, tailwind.css, last webui.tgz

Requires: QEMU web VM, korus-web/.env on guest (from prior qemu-redeploy -WebOnly).
Dev loop:  qemu-web-sync.ps1 -> edit webui on host -> qemu-web-sync.ps1 -> F5 in browser
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
. (Join-Path $QemuRoot "lib\Test-KorusWebHotswap.ps1")
. (Join-Path $QemuRoot "lib\Get-KorusQemuHostHealth.ps1")

if ($Status) {
    $active = $false
    if (Test-KorusQemuStackRunning -RunDir $RunDir) {
        $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
        if ($hk) { $active = Test-KorusGuestWebHotswapActive -HostKey $hk }
    }
    $webuiTgz = Join-Path $RunDir "webui.tgz"
    $syncAt = if (Test-Path $webuiTgz) { (Get-Item $webuiTgz).LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss") } else { "never" }
    Write-Host "=== web hotswap status ===" -ForegroundColor Cyan
    Write-Host "  hotswap compose: $(if ($active) { 'active' } else { 'off' })"
    Write-Host "  UI :19088:       $(Test-KorusHostUiReady)"
    Write-Host "  tailwind.css:    $(Test-KorusHostTailwindCss)"
    Write-Host "  last webui.tgz:  $syncAt"
    if (-not $active) {
        Write-Host "  fix: .\scripts\qemu-dev-mode.ps1 -Mode enable-hotswap" -ForegroundColor Yellow
    }
    exit 0
}

if (-not $Enable -and -not $SyncOnly) {
    Write-Error "Specify -Enable, -SyncOnly, or -Status"
}

if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Write-Error "QEMU not running. Start: .\scripts\qemu-up.ps1 -KeepDisks"
}

$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
if (-not $hk) { throw "web SSH host key not ready" }

if ($SyncOnly) {
    & (Join-Path $Root "scripts\qemu-web-sync.ps1")
    exit $LASTEXITCODE
}

Write-Host "=== QEMU web hotswap enable $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
Write-Host "Syncing guest repo (korus-web compose)..." -ForegroundColor Yellow
. (Join-Path $QemuRoot "lib\Start-KorusRepoHttp.ps1")
. (Join-Path $QemuRoot "lib\New-KorusRepoSnapshot.ps1")
. (Join-Path $QemuRoot "lib\Update-KorusGuestRepo.ps1")
Start-KorusRepoHttp | Out-Null
New-KorusRepoSnapshot -Force | Out-Null
Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $hk -Plink $Plink | Out-Null
Write-Host "Syncing webui (locales + tailwind)..." -ForegroundColor Yellow
Sync-KorusGuestWebui -SshPort 12222 -HostKey $hk -Plink $Plink | Out-Null
Write-Host "Switching guest to docker-compose.hotswap-qemu.yml (no build)..." -ForegroundColor Yellow
try {
    Enable-KorusGuestWebHotswap -SshPort 12222 -HostKey $hk -Plink $Plink | Out-Null
} catch {
    Write-Host "Hotswap enable failed. Run sync-web first: .\scripts\qemu-dev-mode.ps1 -Mode sync-web" -ForegroundColor Red
    throw
}
Write-Host "[OK] Hotswap enabled - use .\scripts\qemu-web-sync.ps1 for fast UI iterations" -ForegroundColor Green
Write-Host "  UI: http://127.0.0.1:19088/" -ForegroundColor DarkGray
