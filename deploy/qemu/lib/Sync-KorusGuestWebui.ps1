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
test -f docker-compose.hotswap-qemu.yml || { echo "missing docker-compose.hotswap-qemu.yml"; exit 1; }
sudo docker compose --env-file .env -f docker-compose.hotswap-qemu.yml down 2>/dev/null || true
sudo docker compose --env-file .env -f docker-compose.yml down 2>/dev/null || true
sudo docker compose --env-file .env -f docker-compose.hotswap-qemu.yml build lb
sudo docker compose --env-file .env -f docker-compose.hotswap-qemu.yml up -d --no-build
ok=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  if curl -fsS http://127.0.0.1:9088/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" != "1" ]; then echo "health check failed"; exit 1; fi
if ! sudo docker exec "$(sudo docker compose --env-file .env -f docker-compose.hotswap-qemu.yml ps -q lb)" grep -q 'location /ws' /etc/nginx/nginx.conf; then
  echo "lb missing /ws location"; exit 1
fi
echo hotswap-enabled
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}
