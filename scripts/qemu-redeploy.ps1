# Push current workspace to running QEMU guests and redeploy via Ansible (spec 003).

param(

    [switch]$ServerOnly,

    [switch]$WebOnly,

    [switch]$Help

)



$ErrorActionPreference = "Stop"

if ($Help) {

    Write-Host @"

Redeploy code into running QEMU VMs (no disk reset). Uses Ansible playbooks on each guest.



  .\scripts\qemu-redeploy.ps1           # server + web (KORUS_BUILD=1)

  .\scripts\qemu-redeploy.ps1 -ServerOnly

  .\scripts\qemu-redeploy.ps1 -WebOnly



Requires: VMs up, repo HTTP on host (started by qemu-up).

See deploy/qemu/README.md

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
. (Join-Path $Lib "Korus-DockerImageCache.ps1")
. (Join-Path $Lib "Test-KorusQemuProcess.ps1")

$lanIp = Write-KorusQemuLanHostInfo -RunDir $RunDir

Write-Host "LAN host IP for web WS: $lanIp" -ForegroundColor DarkGray



function Invoke-RemoteSh {

    param([string]$HostKey, [int]$Port, [string]$Script)

    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script $Script

}

function Wait-KorusEd25519HostKey {
    param(
        [string]$SerialPath,
        [string]$Role,
        [int]$SshPort,
        [int]$MaxMinutes = 15
    )
    $deadline = (Get-Date).AddMinutes($MaxMinutes)
    while ((Get-Date) -lt $deadline) {
        $hk = Get-KorusEd25519HostKey -SerialPath $SerialPath -Role $Role -SshPort $SshPort
        if ($hk) { return $hk }
        Write-Host "  waiting SSH host key ($Role, port $SshPort)..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 30
    }
    throw "$Role SSH host key not available after ${MaxMinutes}m (see $SerialPath)"
}



if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {

    Write-Error "No Korus QEMU VMs running (server.pid/web.pid). Start with: .\scripts\qemu-up.ps1 -KeepDisks"

}



Write-Host "=== Korus QEMU redeploy (Ansible) $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan

Start-KorusRepoHttp | Out-Null
New-KorusRepoSnapshot -Force | Out-Null
Start-KorusDockerImageCacheBackground | Out-Null

$doServer = -not $WebOnly

$doWeb = -not $ServerOnly

$lockRoles = @()
if ($doServer) { $lockRoles += "server" }
if ($doWeb) { $lockRoles += "web" }
foreach ($lockRole in $lockRoles) {
    $lock = Join-Path $RunDir "qemu-redeploy-$lockRole.lock"
    if (Test-Path $lock) {
        $age = ((Get-Date) - (Get-Item $lock).LastWriteTime).TotalMinutes
        if ($age -lt 45) {
            Write-Error "qemu-redeploy-$lockRole already running (${age}m). See deploy\qemu\run\status-remediate.log"
        }
        Remove-Item $lock -Force -ErrorAction SilentlyContinue
    }
    Set-Content -Path $lock -Value ((Get-Date).ToString("o")) -Encoding ascii
}
try {

if ($doServer) {

    $hk = Wait-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221

    if (-not $hk) { throw "server SSH host key not in serial log yet" }

    Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink

    Write-Host "Ansible redeploy on server guest..." -ForegroundColor Yellow

    $serverCmd = @'
set -euo pipefail
export KORUS_BUILD=1 KORUS_REPO_ROOT=/mnt/korus
sed -i 's/\r$//' /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
chmod +x /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
rm -f /var/run/korus-redeploy.done
nohup sudo bash -c '
  export KORUS_BUILD=1 KORUS_REPO_ROOT=/mnt/korus
  sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-guest-deps.sh
  env KORUS_TRUNCATE_BOOTSTRAP_LOG=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh server
  touch /var/run/korus-redeploy.done
' >>/var/log/korus-redeploy-host.log 2>&1 &
echo redeploy-nohup-started
'@

    Invoke-RemoteSh -HostKey $hk -Port 12221 -Script $serverCmd

    Write-Host "Waiting for server redeploy (guest nohup)..." -ForegroundColor Yellow
    $deadline = (Get-Date).AddMinutes(90)
    $ok = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 20
        try {
            $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health/ready" 2>$null
            if ($code -match '^2') { $ok = $true; break }
        } catch {}
        $done = Invoke-RemoteSh -HostKey $hk -Port 12221 -Script "test -f /var/run/korus-redeploy.done && echo done || echo pending" 2>$null
        if ($done -match 'done' -and $code -match '^2') { $ok = $true; break }
    }
    if (-not $ok) { throw "server redeploy did not become ready within 90m (see guest /var/log/korus-bootstrap.log)" }

    Write-Host "[OK] server stack redeployed (Ansible)" -ForegroundColor Green

}



if ($doWeb) {

    $hk = Wait-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222

    if (-not $hk) { throw "web SSH host key not in serial log yet" }

    Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $hk -Plink $Plink

    Write-Host "Ansible redeploy on web guest..." -ForegroundColor Yellow

    $webCmd = @'
set -euo pipefail
export KORUS_BUILD=1 KORUS_REPO_ROOT=/mnt/korus
sed -i 's/\r$//' /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
chmod +x /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
sudo sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-guest-deps.sh
sudo sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-console-setup.sh web
sudo env KORUS_BUILD=1 KORUS_REPO_ROOT=/mnt/korus KORUS_TRUNCATE_BOOTSTRAP_LOG=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh web
curl -fsS http://127.0.0.1:9088/health 2>/dev/null || exit 1
'@

    Invoke-RemoteSh -HostKey $hk -Port 12222 -Script $webCmd

    Write-Host "[OK] web stack redeployed (Ansible)" -ForegroundColor Green

}



Write-Host ""

Write-Host "Host checks:" -ForegroundColor Cyan

foreach ($u in @("http://127.0.0.1:18080/api/v1/health", "http://127.0.0.1:19088/")) {

    $c = curl.exe -sS -m 10 -o NUL -w "%{http_code}" $u 2>$null

    if ($c -match '^2') { Write-Host "  [OK] $u -> $c" -ForegroundColor Green }

    elseif ($c -eq '401') { Write-Host "  [!!] $u -> 401 (wait for stack or redeploy server)" -ForegroundColor Yellow }

    else { Write-Host "  [--] $u -> $c" -ForegroundColor Yellow }

}
} finally {
    foreach ($lockRole in $lockRoles) {
        Remove-Item (Join-Path $RunDir "qemu-redeploy-$lockRole.lock") -Force -ErrorAction SilentlyContinue
    }
}

