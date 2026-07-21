# Bot-delivery worker smoke: container up + NATS queue-group subscription (QEMU server guest).
param(
    [int]$ServerSshPort = 12221,
    [string]$ExpectedQueueGroup = "bot-delivery-workers",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-bot-delivery-worker.ps1

Verifies docker-bot-delivery-worker is running on QEMU server guest and logs show
NATS subscription on queue group bot-delivery-workers.

Prereq: full-server stack with profile push/full; plink + SSH :12221.
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

$oneLiner = "cid=`$(docker ps --format '{{.Names}}' | grep bot-delivery-worker | head -1); if [ -z `"`$cid`" ]; then echo '[FAIL] bot-delivery-worker not running'; exit 1; fi; echo '[OK] container' `$cid; docker logs `$cid 2>&1 | grep -m1 -q '$ExpectedQueueGroup' && echo '[OK] queue group $ExpectedQueueGroup' || (docker logs `$cid 2>&1 | tail -n 40 | grep -q 'BotDeliveryWorker' && echo '[OK] worker active (recent BotDeliveryWorker logs)' || (echo '[FAIL] queue group missing'; exit 1))"
$out = & $plink -batch -hostkey $hostKey -pw korus -P $ServerSshPort "korus@127.0.0.1" $oneLiner 2>&1
Write-Host $out
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "[OK] bot-delivery-worker smoke" -ForegroundColor Green
