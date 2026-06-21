# Launch WAR smokes on QEMU server guest (core-api + ws-gateway). Poll: qemu-guest-job.ps1 -Loop -Job container-portability-smoke
param(
    [switch]$Wait,
    [switch]$SkipSync,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-container-portability-guest.ps1 [-Wait] [-SkipSync]

Runs on QEMU server guest (Docker, not Windows host):
  scripts/smoke-core-api-war-guest.sh
  scripts/smoke-ws-gateway-war-guest.sh

Default: launch-only (poll every 3 min):
  .\scripts\qemu-guest-job.ps1 -Job container-portability-smoke -Loop

Prereq: QEMU server up, full-server stack healthy (:18080).
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
. (Join-Path $Root "deploy\qemu\lib\Invoke-KorusGuestRemoteJob.ps1")

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Fail "QEMU not running. Start: .\scripts\qemu-up.ps1"
}

try {
    $hc = Invoke-WebRequest -Uri "http://127.0.0.1:18080/api/v1/health" -UseBasicParsing -TimeoutSec 8
    if ($hc.StatusCode -ne 200) { Fail "API health not 200 on :18080" }
} catch {
    Fail "API :18080 not reachable; start full stack on server guest first"
}

$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk) { Fail "server SSH host key not ready" }
if (-not (Test-Path $Plink)) { Fail "plink not found: $Plink" }

if (-not $SkipSync) {
    Write-Host "Syncing repo to server guest ..." -ForegroundColor Cyan
    Start-KorusRepoHttp | Out-Null
    New-KorusRepoSnapshot -Force | Out-Null
    Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink | Out-Null
}

$guestScript = @'
set -euo pipefail
cd /mnt/korus
sed -i 's/\r$//' scripts/smoke-core-api-war-guest.sh scripts/smoke-ws-gateway-war-guest.sh scripts/lib/*.sh
bash scripts/smoke-core-api-war-guest.sh
bash scripts/smoke-ws-gateway-war-guest.sh
echo "[OK] container-portability guest smokes"
'@

$jobArgs = @{
    Plink      = $Plink
    HostKey    = $hk
    Port       = 12221
    JobName    = "container-portability-smoke"
    Script     = $guestScript
    TimeoutMin = 90
    PollSec    = 180
}
if (-not $Wait) {
    $jobArgs.LaunchOnly = $true
}

$ok = Invoke-KorusGuestRemoteJob @jobArgs
if (-not $Wait) {
    Write-Host "Poll: .\scripts\qemu-guest-job.ps1 -Job container-portability-smoke -Loop" -ForegroundColor Cyan
    exit 0
}
if (-not $ok) { exit 1 }
Write-Host "[OK] container-portability guest smokes" -ForegroundColor Green
exit 0
