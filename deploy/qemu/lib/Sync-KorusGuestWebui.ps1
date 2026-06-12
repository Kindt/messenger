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
test -f .env || { echo "missing korus-web/.env — run qemu-redeploy -WebOnly once first"; exit 1; }
test -f docker-compose.hotswap-qemu.yml || { echo "missing docker-compose.hotswap-qemu.yml"; exit 1; }
docker compose --env-file .env -f docker-compose.yml down 2>/dev/null || true
docker compose --env-file .env -f docker-compose.hotswap-qemu.yml up -d --no-build
curl -fsS http://127.0.0.1:9088/health
echo hotswap-enabled
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}
