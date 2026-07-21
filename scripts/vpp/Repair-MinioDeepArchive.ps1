# Free MinIO space + rebuild deep-archiver on QEMU server guest (VPP addon-deep-archive).
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $Root 'deploy\qemu\lib\Update-KorusGuestRepo.ps1')

$plink = Join-Path $env:ProgramFiles 'PuTTY\plink.exe'
$runDir = Join-Path $Root 'deploy\qemu\run'
$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir 'server-serial.log') -Role server -SshPort 12221
if (-not $hostKey) { throw 'server SSH host key not ready' }

. (Join-Path $Root 'deploy\qemu\lib\New-KorusRepoSnapshot.ps1')

Write-Host 'Repair: MinIO prune + deep-archiver recreate on server guest...' -ForegroundColor Cyan
try {
    New-KorusRepoSnapshot -Force | Out-Null
    Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hostKey -Plink $plink | Out-Null
} catch {
    Write-Host "  repo snapshot sync skipped: $_" -ForegroundColor DarkYellow
}
$guestScript = @'
set -e
docker exec docker-minio-1 mc alias set local http://127.0.0.1:9000 avandocmsg avandocmsg123
docker exec docker-minio-1 mc rm --recursive --force local/avandocmsg/messages/ 2>/dev/null || true
docker exec docker-minio-1 mc rm --recursive --force local/avandocmsg/deep-archive/ 2>/dev/null || true
docker builder prune -af >/dev/null 2>&1 || true
docker image prune -af >/dev/null 2>&1 || true
cd /mnt/korus/docker
docker compose -f docker-compose.full-server.yml -f docker-compose.fleet-lab.yml -f docker-compose.qemu-regression-lab.yml up -d --force-recreate deep-archiver-worker
for i in $(seq 1 30); do
  st=$(docker inspect -f '{{.State.Health.Status}}' docker-deep-archiver-worker-1 2>/dev/null || echo missing)
  echo deep-archiver-health=$st
  [ "$st" = healthy ] && break
  sleep 5
done
curl -sf http://127.0.0.1:9196/health && echo minio-repair-ok
'@

$out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12221 -Script $guestScript
Write-Host $out
if ($out -notmatch 'minio-repair-ok') {
    throw 'MinIO deep-archive repair did not confirm deep-archiver health'
}
Write-Host '[OK] MinIO pruned, deep-archiver recreated' -ForegroundColor Green
