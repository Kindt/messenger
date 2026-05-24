# Push current workspace to running QEMU guests and rebuild server API + web UI stacks.
param(
    [switch]$ServerOnly,
    [switch]$WebOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Redeploy code into running QEMU VMs (no disk reset).

  .\scripts\qemu-redeploy.ps1           # server core-api + full web stack
  .\scripts\qemu-redeploy.ps1 -ServerOnly
  .\scripts\qemu-redeploy.ps1 -WebOnly

Requires: VMs up, repo HTTP on host (started by qemu-up).
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$QemuRoot = Join-Path $Root "deploy\qemu"
$Lib = Join-Path $QemuRoot "lib"
$RunDir = Join-Path $QemuRoot "run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"

. (Join-Path $QemuRoot "config.ps1")
. (Join-Path $Lib "New-KorusRepoSnapshot.ps1")
. (Join-Path $Lib "Start-KorusRepoHttp.ps1")
. (Join-Path $Lib "Update-KorusGuestRepo.ps1")
. (Join-Path $Lib "Get-KorusLanHostIp.ps1")
$lanIp = Read-KorusQemuLanHostIp -RunDir $RunDir
Write-Host "LAN host IP for web WS: $lanIp" -ForegroundColor DarkGray

function Invoke-RemoteSh {
    param([string]$HostKey, [int]$Port, [string]$Script)
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script $Script
}

if (-not (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)) {
    Write-Error "No QEMU VMs running. Start with: .\scripts\qemu-up.ps1 -KeepDisks"
}

Write-Host "=== Korus QEMU redeploy $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan
Start-KorusRepoHttp | Out-Null

$doServer = -not $WebOnly
$doWeb = -not $ServerOnly

if ($doServer) {
    $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server
    if (-not $hk) { throw "server SSH host key not in serial log yet" }
    Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink
    Write-Host "Rebuilding core-api on server..." -ForegroundColor Yellow
    $serverCmd = @'
cd /mnt/korus
docker compose -f docker/docker-compose.full-server.yml -f docker/docker-compose.lan-publish.yml build core-api
docker compose -f docker/docker-compose.full-server.yml -f docker/docker-compose.lan-publish.yml up -d
for i in 1 2 3 4 5 6 7 8 9 10 12 15 18 24 30; do curl -fsS http://127.0.0.1:8081/realms/avandocmsg >/dev/null 2>&1 && break; sleep 5; done
# Script can arrive with CRLF from Windows checkout; normalize before running on Linux guest.
sed -i 's/\r$//' /mnt/korus/scripts/keycloak-ensure-dev-users.sh || true
KEYCLOAK_URL=http://127.0.0.1:8081 /bin/sh /mnt/korus/scripts/keycloak-ensure-dev-users.sh || true
for i in 1 2 3 4 5 6 7 8 9 10 12 15 18 24 30; do curl -fsS http://127.0.0.1:8080/api/v1/health && exit 0; sleep 5; done
exit 1
'@
    Invoke-RemoteSh -HostKey $hk -Port 12221 -Script $serverCmd
    Write-Host "[OK] server core-api rebuilt" -ForegroundColor Green
}

if ($doWeb) {
    $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web
    if (-not $hk) { throw "web SSH host key not in serial log yet" }
    Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $hk -Plink $Plink
    Write-Host "Rebuilding web stack..." -ForegroundColor Yellow
    $webCmd = @"
HOST_GW=10.0.2.2
for i in 1 2 3 4 5 6 7 8 9 10 12 15 18 24 30; do curl -fsS "http://`${HOST_GW}:18080/api/v1/health" >/dev/null 2>&1 && break; echo waiting for API...; sleep 5; done
cd /mnt/korus/korus-web
printf '%s\n' "KORUS_WEB_LB_PORT=9088" "KORUS_SERVER_HOST=`${HOST_GW}" "WEB_CLIENT_API_UPSTREAM=http://`${HOST_GW}:18080" "WEB_CLIENT_WS_PUBLIC_URL=ws://${lanIp}:19088/ws" "KORUS_WS_GATEWAY_HOST=`${HOST_GW}" "KORUS_WS_GATEWAY_PORT=18082" | sudo tee .env >/dev/null
docker compose --env-file .env -f docker-compose.yml build
docker compose --env-file .env -f docker-compose.yml up -d --force-recreate
docker compose --env-file .env -f docker-compose.yml ps
"@
    Invoke-RemoteSh -HostKey $hk -Port 12222 -Script $webCmd
    Write-Host "[OK] web stack rebuilt" -ForegroundColor Green
}

Write-Host ""
Write-Host "Host checks:" -ForegroundColor Cyan
foreach ($u in @("http://127.0.0.1:18080/api/v1/health", "http://127.0.0.1:19088/")) {
    $c = curl.exe -sS -m 10 -o NUL -w "%{http_code}" $u 2>$null
    if ($c -match '^2') { Write-Host "  [OK] $u -> $c" -ForegroundColor Green }
    elseif ($c -eq '401') { Write-Host "  [!!] $u -> 401 (rebuild core-api or wait)" -ForegroundColor Yellow }
    else { Write-Host "  [--] $u -> $c" -ForegroundColor Yellow }
}
