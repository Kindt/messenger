# Push current workspace to running QEMU guests and redeploy via Ansible (spec 003).
param(
    [switch]$ServerOnly,
    [switch]$WebOnly,
    [switch]$Rebuild,
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"

Redeploy code into running QEMU VMs (no disk reset). Uses Ansible playbooks on each guest.

  .\scripts\qemu-redeploy.ps1              # server + web, sync (no docker build)
  .\scripts\qemu-redeploy.ps1 -ServerOnly
  .\scripts\qemu-redeploy.ps1 -WebOnly
  .\scripts\qemu-redeploy.ps1 -Rebuild     # full docker compose --build (slow)
  .\scripts\qemu-redeploy.ps1 -Force       # redeploy even if host health OK

Default: KORUS_BUILD=0 (sync). Use -Rebuild for Dockerfile/Gradle changes.

Requires: VMs up, repo HTTP on host (started by qemu-up).
See deploy/qemu/README.md and docs/plans/2026-06-12-qemu-dev-modes-stabilization-design.md

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
. (Join-Path $Lib "Get-KorusQemuHostHealth.ps1")
. (Join-Path $Lib "Get-KorusGuestBootstrapPhase.ps1")
. (Join-Path $Lib "Korus-QemuRedeployLock.ps1")

$buildFlag = if ($Rebuild) { "1" } else { "0" }
$mode = if ($Rebuild) { "rebuild" } else { "sync" }

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

function Wait-KorusRedeployReady {
    param(
        [string]$Role,
        [string]$HostKey,
        [int]$Port,
        [int]$MaxMinutes,
        [scriptblock]$TestReady
    )
    $deadline = (Get-Date).AddMinutes($MaxMinutes)
    $ok = $false
    $tick = 0
    $progress = @{ phase = ""; fp = ""; stuckMin = 0 }
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 20
        $tick++
        if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
            throw "QEMU VM(s) died during $Role redeploy wait; restart: .\scripts\qemu-up.ps1 -KeepDisks"
        }
        if ($tick % 3 -eq 0) {
            Write-KorusGuestBootstrapProgress -Role $Role -HostKey $HostKey -Port $Port -Plink $Plink -State $progress
        }
        if (& $TestReady) { $ok = $true; break }
        try {
            $done = Invoke-RemoteSh -HostKey $HostKey -Port $Port -Script "test -f /var/run/korus-redeploy.done && echo done || echo pending"
            if ($done -match 'done' -and (& $TestReady)) { $ok = $true; break }
        } catch {}
    }
    if (-not $ok) {
        throw "$Role redeploy did not become ready within ${MaxMinutes}m (see guest /var/log/korus-bootstrap.log)"
    }
}

if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) {
    Write-Error "No Korus QEMU VMs running (server.pid/web.pid). Start with: .\scripts\qemu-up.ps1 -KeepDisks"
}

Write-Host "=== Korus QEMU redeploy mode=$mode $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Cyan

Start-KorusRepoHttp | Out-Null
New-KorusRepoSnapshot -Force | Out-Null
if ($Rebuild) {
    Start-KorusDockerImageCacheBackground | Out-Null
}

$doServer = -not $WebOnly
$doWeb = -not $ServerOnly

$lockRoles = @()
if ($doServer) { $lockRoles += "server" }
if ($doWeb) { $lockRoles += "web" }
foreach ($lockRole in $lockRoles) {
    if (-not (Enter-KorusRedeployLock -RunDir $RunDir -Role $lockRole -ProcessId $PID)) {
        $lock = Get-KorusRedeployLockPath -RunDir $RunDir -Role $lockRole
        $age = if (Test-Path -LiteralPath $lock) {
            [math]::Round(((Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime).TotalMinutes, 1)
        } else { 0 }
        Write-Error "qemu-redeploy-$lockRole already running (${age}m). See deploy\qemu\run\status-remediate.log"
    }
}
try {

if ($doServer) {
    if (-not $Force -and (Test-KorusHostApiReady)) {
        Write-Host "[OK] server already ready on host (use -Force or -Rebuild to redeploy)" -ForegroundColor Green
    } else {
        $hk = Wait-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
        if (-not $hk) { throw "server SSH host key not in serial log yet" }

        Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink

        Write-Host "Ansible redeploy on server guest (KORUS_BUILD=$buildFlag)..." -ForegroundColor Yellow

        $serverCmd = @"
set -euo pipefail
export KORUS_BUILD=$buildFlag KORUS_REPO_ROOT=/mnt/korus
sed -i 's/\r$//' /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
chmod +x /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
sudo rm -f /var/run/korus-redeploy.done
nohup sudo bash -c '
  export KORUS_BUILD=$buildFlag KORUS_REPO_ROOT=/mnt/korus
  sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-guest-deps.sh
  env KORUS_TRUNCATE_BOOTSTRAP_LOG=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh server
  touch /var/run/korus-redeploy.done
' >>/tmp/korus-redeploy-host.log 2>&1 &
echo redeploy-nohup-started
"@

        Invoke-RemoteSh -HostKey $hk -Port 12221 -Script $serverCmd

        Write-Host "Waiting for server redeploy (guest nohup)..." -ForegroundColor Yellow
        Wait-KorusRedeployReady -Role server -HostKey $hk -Port 12221 -MaxMinutes 90 -TestReady { Test-KorusHostApiReady }
        Write-Host "[OK] server stack redeployed (Ansible, mode=$mode)" -ForegroundColor Green
    }
}

if ($doWeb) {
    if (-not $Force -and (Test-KorusHostUiReady)) {
        Write-Host "[OK] web already ready on host (use -Force or -Rebuild to redeploy)" -ForegroundColor Green
    } else {
        $hk = Wait-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort 12222
        if (-not $hk) { throw "web SSH host key not in serial log yet" }

        Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $hk -Plink $Plink

        Write-Host "Ansible redeploy on web guest (KORUS_BUILD=$buildFlag)..." -ForegroundColor Yellow

        $webCmd = @"
set -euo pipefail
export KORUS_BUILD=$buildFlag KORUS_REPO_ROOT=/mnt/korus
sed -i 's/\r$//' /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
chmod +x /mnt/korus/deploy/qemu/vm-bootstrap/*.sh || true
sudo rm -f /var/run/korus-redeploy.done
nohup sudo bash -c '
  export KORUS_BUILD=$buildFlag KORUS_REPO_ROOT=/mnt/korus
  sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-guest-deps.sh
  sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-console-setup.sh web
  env KORUS_TRUNCATE_BOOTSTRAP_LOG=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh web
  touch /var/run/korus-redeploy.done
' >>/tmp/korus-redeploy-host.log 2>&1 &
echo redeploy-nohup-started
"@

        Invoke-RemoteSh -HostKey $hk -Port 12222 -Script $webCmd

        Write-Host "Waiting for web redeploy (guest nohup)..." -ForegroundColor Yellow
        Wait-KorusRedeployReady -Role web -HostKey $hk -Port 12222 -MaxMinutes 45 -TestReady { Test-KorusHostUiReady }
        Write-Host "[OK] web stack redeployed (Ansible, mode=$mode)" -ForegroundColor Green
    }
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
        Exit-KorusRedeployLock -RunDir $RunDir -Role $lockRole
    }
}
