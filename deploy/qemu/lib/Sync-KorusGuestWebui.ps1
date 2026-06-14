function Sync-KorusGuestWebui {
    param(
        [int]$SshPort = 12222,
        [string]$HostKey,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe",
        [switch]$SkipTailwind
    )
    . (Join-Path $PSScriptRoot "New-KorusWebuiSnapshot.ps1")
    . (Join-Path $PSScriptRoot "Start-KorusRepoHttp.ps1")
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")

    if (-not $HostKey) { throw "SSH host key required" }
    if (-not (Test-Path $Plink)) { throw "PuTTY plink not found at $Plink" }

    if (-not $SkipTailwind) {
        New-KorusWebuiSnapshot -Force | Out-Null
    } else {
        New-KorusWebuiSnapshot | Out-Null
    }
    Start-KorusRepoHttp | Out-Null

    $cmd = @'
set -euo pipefail
dest=/mnt/korus/modules/web-client/src/main/resources
sudo mkdir -p "$dest"
curl -fsSL http://10.0.2.2:18890/webui.tgz | sudo tar -xzf - -C "$dest"
sudo find "$dest/webui" -name '*.sh' -exec sed -i 's/\r$//' {} \; 2>/dev/null || true
echo webui-synced
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}

function Enable-KorusGuestWebHotswap {
    param(
        [int]$SshPort = 12222,
        [string]$HostKey,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    )
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")

    if (-not $HostKey) { throw "SSH host key required" }
    $cmd = @'
set -euo pipefail
cd /mnt/korus/korus-web
test -f .env || { echo "missing korus-web/.env - run qemu-redeploy -WebOnly once first"; exit 1; }
base=docker-compose.yml
overlay=docker-compose.qemu-hotswap-overlay.yml
if [ ! -f "$overlay" ]; then
  overlay=docker-compose.qemu-full-hotswap.yml
  compose_files="-f $overlay"
else
  compose_files="-f $base -f $overlay"
fi
if ! sudo docker image inspect korus-messenger-web-client:local >/dev/null 2>&1; then
  echo "missing web-client image - run qemu-redeploy -WebOnly -Rebuild first"; exit 1
fi
sudo docker compose --env-file .env $compose_files up -d web-a web-b 2>/dev/null || true
sudo docker compose --env-file .env $compose_files build lb
sudo docker compose --env-file .env $compose_files up -d --remove-orphans
ok=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  if curl -fsS http://127.0.0.1:9088/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" != "1" ]; then echo "health check failed"; exit 1; fi
lb_id=$(sudo docker compose --env-file .env $compose_files ps -q lb)
if ! sudo docker exec "$lb_id" grep -q 'location /ws' /etc/nginx/nginx.conf; then
  echo "lb missing /ws location"; exit 1
fi
if ! sudo docker compose --env-file .env $compose_files ps -q web-dev >/dev/null 2>&1; then
  echo "web-dev not running"; exit 1
fi
echo hotswap-active
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}

function Disable-KorusGuestWebHotswap {
    param(
        [int]$SshPort = 12222,
        [string]$HostKey,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    )
    if (-not $HostKey) { throw "SSH host key required" }
    $cmd = @'
set -euo pipefail
cd /mnt/korus/korus-web
test -f .env || { echo "missing korus-web/.env"; exit 1; }
if [ -f docker-compose.qemu-hotswap-overlay.yml ]; then
  sudo docker compose --env-file .env -f docker-compose.yml -f docker-compose.qemu-hotswap-overlay.yml stop web-dev 2>/dev/null || true
  sudo docker compose --env-file .env -f docker-compose.yml -f docker-compose.qemu-hotswap-overlay.yml rm -f web-dev 2>/dev/null || true
fi
for f in docker-compose.qemu-full-hotswap.yml docker-compose.hotswap-qemu.yml; do
  if [ -f "$f" ]; then
    sudo docker compose --env-file .env -f "$f" stop web-dev 2>/dev/null || true
    sudo docker compose --env-file .env -f "$f" rm -f web-dev 2>/dev/null || true
  fi
done
sudo docker compose --env-file .env -f docker-compose.yml up -d --force-recreate lb web-a web-b
ok=0
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  if curl -fsS http://127.0.0.1:9088/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" != "1" ]; then echo "full compose health check failed"; exit 1; fi
echo full-compose-active
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}
