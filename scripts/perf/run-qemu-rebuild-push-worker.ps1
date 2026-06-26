# Rebuild push-worker image on QEMU server guest (FR-068 /metrics on :9194).
param(
    [switch]$NoCache,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\perf\run-qemu-rebuild-push-worker.ps1 [-NoCache]"
    exit 0
}

. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
. (Join-Path $Root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")
. (Join-Path $Root "deploy\qemu\lib\New-KorusRepoSnapshot.ps1")
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk) { throw "server SSH host key not ready" }

Write-Host "Syncing repo to server guest..." -ForegroundColor Yellow
Start-KorusRepoHttp | Out-Null
New-KorusRepoSnapshot -Force | Out-Null
Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink | Out-Null

$buildLine = if ($NoCache) {
    'docker compose $COMPOSE_FILES --profile push build --no-cache push-worker'
} else {
    'docker compose $COMPOSE_FILES --profile push build push-worker'
}

$script = @'
set -euo pipefail
cd /mnt/korus/docker
COMPOSE_FILES="-f docker-compose.full-server.yml"
if [ -f docker-compose.qemu-lab.yml ]; then
  COMPOSE_FILES="$COMPOSE_FILES -f docker-compose.qemu-lab.yml"
fi
'@ + "`n$buildLine`n" + @'
docker compose $COMPOSE_FILES --profile push up -d --force-recreate push-worker
for i in $(seq 1 24); do
  hc=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:9194/health || echo fail)
  mc=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:9194/metrics || echo fail)
  echo "health=$hc metrics=$mc"
  if [ "$hc" = "200" ] && [ "$mc" = "200" ]; then
    echo "[OK] push-worker /health and /metrics"
    exit 0
  fi
  sleep 5
done
echo "[FAIL] push-worker metrics probe timeout"
docker logs --tail 30 $(docker ps -q --filter name=push-worker | head -1) 2>&1 | tail -30
exit 1
'@

$out = Invoke-QemuServerGuest -Script $script
Write-Host $out
if ($out -match '\[FAIL\]') { exit 1 }
Write-Host "[OK] push-worker rebuild on guest" -ForegroundColor Green
