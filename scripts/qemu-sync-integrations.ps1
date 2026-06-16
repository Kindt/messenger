# Sync repo to korus-integrations guest and rebuild compose stack (spec 014).
param(
    [switch]$BuildOnly,
    [switch]$MocksOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-sync-integrations.ps1 [-BuildOnly] [-MocksOnly]

Requires: korus-integrations VM (.\scripts\qemu-integrations-up.ps1), repo HTTP :18890.
Updates /mnt/korus from repo.tgz, then docker compose build + up on guest.
-MocksOnly: sync repo + restart mock-apis only (no image rebuild; use when Docker Hub unreachable).
Typical full sync: 5-20 min depending on image cache.
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")

if (-not (Test-Path $plink)) { throw "PuTTY plink not found: $plink" }

$tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "Integrations SSH :12223 down - run .\scripts\qemu-integrations-up.ps1" -ForegroundColor Red
    exit 2
}

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "integrations-serial.log") -Role integrations -SshPort 12223
if (-not $hostKey) { throw "integrations SSH host key not ready (see integrations-serial.log)" }

if (-not $BuildOnly) {
    . (Join-Path $root "deploy\qemu\lib\New-KorusRepoSnapshot.ps1")
    Stop-KorusRepoHttp
    New-KorusRepoSnapshot -StopRepoHttp -Force | Out-Null
    Start-KorusRepoHttp | Out-Null
    Write-Host "Syncing repo to integrations guest..." -ForegroundColor Cyan
    Update-KorusGuestRepo -Role integrations -SshPort 12223 -HostKey $hostKey -Plink $plink | Out-Null
}

if ($MocksOnly) {
    $composeCmd = @'
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml up -d mock-apis 2>&1
sudo docker compose -f docker-compose.integrations.yml restart mock-apis 2>&1
curl -sf http://127.0.0.1:8080/health && echo mock-apis-ok
curl -sf http://127.0.0.1:8090/health && echo integrations-gateway-ok
'@
} else {
    $composeCmd = @'
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml build ai-bridge ocr-worker onec-bridge exchange-bridge storage-bridge 2>&1
sudo docker compose -f docker-compose.integrations.yml up -d --remove-orphans 2>&1
curl -sf http://127.0.0.1:8090/health && echo integrations-gateway-ok
'@
}

Write-Host "Rebuilding integrations compose on guest..." -ForegroundColor Cyan
$out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12223 -Script $composeCmd
Write-Host $out
if ($out -notmatch 'integrations-gateway-ok') {
    Write-Host "[WARN] gateway health not confirmed; run .\scripts\smoke-integrations-gate.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] integrations guest synced" -ForegroundColor Green
