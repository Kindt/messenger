# Full QEMU stack + hotswap UI on Windows host (all containers in VMs, browser -> web-dev overlay).
param(
    [switch]$FreshDisks,
    [switch]$Rebuild,
    [switch]$Force,
    [switch]$SkipVmUp,
    [switch]$SkipHotswap,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"

Full QEMU stack with hotswap (default): all server containers + web-a/web-b/web-dev/lb.

Uses separate QEMU disks (server-full.qcow2, web-full.qcow2) so dev disks are untouched.
Only one profile runs at a time (same host ports). Switching from dev stops dev VMs first.

  .\scripts\qemu-full-stack-up.ps1              # full profile, KeepDisks, redeploy, hotswap
  .\scripts\qemu-full-stack-up.ps1 -Rebuild     # docker compose --build in both VMs
  .\scripts\qemu-full-stack-up.ps1 -FreshDisks    # WIPE full-profile disks only + cold bootstrap
  .\scripts\qemu-full-stack-up.ps1 -SkipHotswap   # prod-like lb -> web-a/web-b only

Dev daily work: .\scripts\qemu-dev-mode.ps1 -Mode warm  (server-dev / web-dev disks)

Server guest: scripts/full-stack-up.sh (full-server compose).
Web guest:    docker-compose.yml + qemu-hotswap-overlay (web-a/b/dev/lb).

UI sync:     .\scripts\qemu-dev-mode.ps1 -Mode sync-ui
Stop:        .\scripts\qemu-full-stack-down.ps1

"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$RunDir = Join-Path $QemuRoot "run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"

. (Join-Path $QemuRoot "lib\Test-KorusQemuProcess.ps1")
. (Join-Path $QemuRoot "lib\Test-KorusWebHotswap.ps1")
. (Join-Path $QemuRoot "lib\Update-KorusGuestRepo.ps1")
. (Join-Path $QemuRoot "lib\Sync-KorusGuestWebui.ps1")
. (Join-Path $QemuRoot "lib\Start-KorusRepoHttp.ps1")
. (Join-Path $QemuRoot "lib\New-KorusRepoSnapshot.ps1")
. (Join-Path $QemuRoot "lib\Get-KorusQemuStackProfile.ps1")

Stop-KorusQemuIfProfileMismatch -TargetProfile full -RunDir $RunDir -QemuDownScript (Join-Path $Root "scripts\qemu-down.ps1")
Set-KorusQemuStackProfile -Profile full

$vmUp = Test-KorusQemuStackRunning -RunDir $RunDir
$fullDisksExist = Test-KorusQemuProfileDisksExist -StackProfile full

if (-not $SkipVmUp) {
    if ($FreshDisks) {
        if ($vmUp) {
            Write-Host "FreshDisks: stopping VMs and resetting full-profile overlay disks..." -ForegroundColor Yellow
            & (Join-Path $Root "scripts\qemu-down.ps1")
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            $vmUp = $false
        }
        . (Join-Path $QemuRoot "lib\Reset-KorusVmDisks.ps1")
        Reset-KorusVmDisks -StackProfile full
        Write-Host "Starting QEMU full profile (cloud-init + KORUS_BUILD=1 on new disks)..." -ForegroundColor Cyan
        & (Join-Path $Root "scripts\qemu-up.ps1") -StackProfile full
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } elseif (-not $fullDisksExist) {
        Write-Host "Full-profile disks missing; creating new server-full / web-full (dev disks preserved)..." -ForegroundColor Cyan
        & (Join-Path $Root "scripts\qemu-up.ps1") -StackProfile full -KeepDisks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } elseif (-not $vmUp) {
        Write-Host "Starting QEMU full profile (KeepDisks)..." -ForegroundColor Cyan
        & (Join-Path $Root "scripts\qemu-up.ps1") -StackProfile full -KeepDisks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } else {
        Write-Host "QEMU VMs already running (full profile, KeepDisks preserved)" -ForegroundColor DarkGray
    }
} elseif (-not $vmUp) {
    Write-Error "QEMU not running. Omit -SkipVmUp or run: .\scripts\qemu-full-stack-up.ps1"
}

$redeployArgs = @{ Force = $true }
if ($Rebuild) { $redeployArgs.Rebuild = $true }
if ($Force) { $redeployArgs.Force = $true }

Write-Host "Ansible redeploy full stack (server + web, mode=$(if ($Rebuild) { 'rebuild' } else { 'sync' }))..." -ForegroundColor Cyan
& (Join-Path $Root "scripts\qemu-redeploy.ps1") @redeployArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Waiting for host-forwarded health..." -ForegroundColor Cyan
& (Join-Path $Root "scripts\qemu-stack-wait.ps1") -MaxMinutes 30
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $SkipHotswap) {
    $whk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
    if (-not $whk) { throw "web SSH host key not ready" }
    Write-Host "Enabling hotswap (web-a/web-b stay up, lb -> web-dev overlay)..." -ForegroundColor Cyan
    Start-KorusRepoHttp | Out-Null
    New-KorusRepoSnapshot -Force | Out-Null
    Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $whk -Plink $Plink | Out-Null
    Sync-KorusGuestWebui -SshPort 12222 -HostKey $whk -Plink $Plink | Out-Null
    Enable-KorusGuestWebHotswap -SshPort 12222 -HostKey $whk -Plink $Plink | Out-Null
}

Write-Host ""
if ($SkipHotswap) {
    Write-Host "[OK] Full QEMU stack ready (web-a/web-b/lb, no hotswap)" -ForegroundColor Green
} else {
    Write-Host "[OK] Full QEMU stack + hotswap (profile=full, dev disks untouched)" -ForegroundColor Green
    Write-Host "  UI sync: .\scripts\qemu-dev-mode.ps1 -Mode sync-ui" -ForegroundColor DarkGray
}
Write-Host "  API: http://127.0.0.1:18080/api/v1/health" -ForegroundColor DarkGray
Write-Host "  UI:  http://127.0.0.1:19088/" -ForegroundColor DarkGray
