# Preview-worker health on QEMU server guest (full-server :9195).
param(
    [int]$ServerSshPort = 12221,
    [string]$HealthPath = "http://127.0.0.1:9195/health",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-preview-worker-qemu.ps1

Probes preview-worker /health inside QEMU server guest via SSH (plink :12221).
Prereq: full-server stack with preview-worker container.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $plink)) { Fail "plink not found: $plink" }

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort $ServerSshPort
if (-not $hostKey) { Fail "server SSH host key not ready" }

$oneLiner = "cid=`$(docker ps --format '{{.Names}}' | grep preview-worker | head -1); if [ -z `"`$cid`" ]; then echo '[FAIL] preview-worker not running'; exit 1; fi; echo '[OK] container' `$cid; body=`$(curl -sf '$HealthPath' || echo ''); if [ `"`$body`" != ok ]; then echo '[FAIL] body expected ok, got:' `$body; exit 1; fi; echo '[OK] preview-worker health $HealthPath'"
$out = & $plink -batch -hostkey $hostKey -pw korus -P $ServerSshPort "korus@127.0.0.1" $oneLiner 2>&1
Write-Host $out
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "[OK] preview-worker QEMU smoke" -ForegroundColor Green
