# Sync repo to server guest and rebuild only core-api (~5-15 min, not full stack).
param(
    [switch]$Help,
    [switch]$ForceLock
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-sync-api-core.ps1

Updates /mnt/korus from repo.tgz and runs rebuild-core-api-guest.sh
(docker compose build core-api && up -d core-api).

Use for Java/API-only changes (e.g. new REST fields, Flyway migrations).
UI-only changes: .\scripts\qemu-dev-mode.ps1 -Mode sync-ui (hotswap).

Prereq: QEMU server VM up, SSH :12221.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"

. (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $Root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")
. (Join-Path $Root "deploy\qemu\lib\New-KorusRepoSnapshot.ps1")
. (Join-Path $Root "deploy\qemu\lib\Korus-QemuGuestTaskLock.ps1")

if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Write-Error "QEMU not running. Start: .\scripts\qemu-dev-mode.ps1 -Mode warm"
}

$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk) { throw "server SSH host key not ready" }

Write-Host "=== sync-api-core $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan

$elapsedSec = 0
Invoke-KorusGuestTaskLocked -RunDir $RunDir -Guest server -TaskName "sync-api-core" -ForceLock:$ForceLock -Action {
    Start-KorusRepoHttp | Out-Null
    New-KorusRepoSnapshot -Force | Out-Null

    Write-Host "Updating repo on server guest..." -ForegroundColor Yellow
    Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink | Out-Null

    $guestScript = @'
set -euo pipefail
sed -i 's/\r$//' /mnt/korus/deploy/qemu/vm-bootstrap/rebuild-core-api-guest.sh
chmod +x /mnt/korus/deploy/qemu/vm-bootstrap/rebuild-core-api-guest.sh
sudo env KORUS_REPO_ROOT=/mnt/korus bash /mnt/korus/deploy/qemu/vm-bootstrap/rebuild-core-api-guest.sh
echo core-api-rebuild-done
'@

    Write-Host "Rebuilding core-api container (Gradle in Docker, not full stack)..." -ForegroundColor Yellow
    Write-Host "  (plink output streams below; long silence is normal during docker build)" -ForegroundColor DarkGray
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $buildOut = Invoke-PlinkShell -Plink $Plink -HostKey $hk -Port 12221 -Script $guestScript
    $sw.Stop()
    $script:elapsedSec = $sw.Elapsed.TotalSeconds
    if ($buildOut -and "$buildOut".Trim()) {
        Write-Host $buildOut
    }
}

Write-Host "[OK] core-api synced in $([math]::Round($elapsedSec, 1))s" -ForegroundColor Green
$c = curl.exe -sS -m 10 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health" 2>$null
Write-Host "  API health: $c" -ForegroundColor $(if ($c -match '^2') { "Green" } else { "Yellow" })
