#Requires -Version 5.1
# VPP-2 plugin platform: all bridges — physical ports + smokes (spec 030). No SKIP.
param(
    [switch]$SkipPhysicalDisconnect,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-plugin-lifecycle.ps1 [-SkipPhysicalDisconnect]

Mandatory full plugin lifecycle: all bridges from plugin-lifecycle-matrix.json.
Requires: .\scripts\qemu-up.ps1 -WithIntegrations
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$matrixPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\plugin-lifecycle-matrix.json"
if (-not (Test-Path $matrixPath)) { Write-Error "missing $matrixPath"; exit 1 }
$matrix = Get-Content -Raw $matrixPath | ConvertFrom-Json

$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Test-TcpPort([int]$Port) {
    try {
        $t = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        return [bool]$t.TcpTestSucceeded
    } catch { return $false }
}

function Test-PluginHttpReachable([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
        return $true
    } catch { return $false }
}

function Invoke-IntegrationsGuest([string]$Script) {
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "integrations-serial.log") -Role integrations -SshPort 12223
    if (-not $hostKey) { throw "integrations SSH host key not ready" }
    Invoke-PlinkShell -Plink $Plink -HostKey $hostKey -Port 12223 -Script $Script
}

if (-not (Test-TcpPort 12223)) {
    Write-Host "[FAIL] integrations VM not up (:12223) - run .\scripts\qemu-up.ps1 -WithIntegrations" -ForegroundColor Red
    exit 1
}

Write-Host "=== plugin catalog: all bridge ports (connected) ===" -ForegroundColor Cyan
$bridges = @($matrix.plugin_bridges)
foreach ($b in $bridges) {
    $port = [int]$b.host_port
    if (-not (Test-TcpPort $port)) {
        throw "plugin bridge $($b.id) port $port not open - physical disconnect or integrations VM down"
    }
    Write-Host "  [OK] $($b.id) :$port" -ForegroundColor DarkGray
}

Write-Host "=== plugin programmatic: smoke each bridge ===" -ForegroundColor Cyan
foreach ($b in $bridges) {
    $smokeRel = $b.smoke
    $smokePath = Join-Path $Root ($smokeRel -replace '/', '\')
    if (-not (Test-Path $smokePath)) { throw "missing smoke for $($b.id): $smokeRel" }
    Write-Host "  -> $($b.id): $smokeRel" -ForegroundColor DarkGray
    $baseArg = if ($smokeRel -match "echo-php") { "http://127.0.0.1:$($b.host_port)" } else { $null }
    if ($baseArg) { & $smokePath -BaseUrl $baseArg } else { & $smokePath }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not $SkipPhysicalDisconnect) {
    Write-Host "=== plugin physical: stop echo-php / verify disconnect ===" -ForegroundColor Cyan
    $stopScript = @'
set -e
cd /mnt/korus/integrations
cid=$(sudo docker ps -q --filter name=echo-php | head -1)
if [ -z "$cid" ]; then
  cid=$(sudo docker ps -q --filter name=integrations-echo-php | head -1)
fi
if [ -z "$cid" ]; then
  echo "[FAIL] echo-php container not found"
  exit 1
fi
sudo docker stop "$cid"
if curl -sf http://127.0.0.1:8088/health >/dev/null 2>&1; then
  echo "[FAIL] echo-php still healthy on guest after stop"
  exit 1
fi
echo stopped=$cid
'@
    Invoke-IntegrationsGuest -Script $stopScript
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Start-Sleep -Seconds 3
    if (Test-PluginHttpReachable "http://127.0.0.1:18088/health") {
        throw "host :18088 still responds after physical echo-php stop"
    }
    Write-Host "  [OK] :18088 unreachable after docker stop" -ForegroundColor DarkGray

    $echoSmoke = Join-Path $Root "scripts\smoke-plugin-echo-php.ps1"
    $smokeFailed = $false
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $echoSmoke -BaseUrl "http://127.0.0.1:18088"
        if ($LASTEXITCODE -ne 0) { $smokeFailed = $true }
    } catch {
        $smokeFailed = $true
    } finally {
        $ErrorActionPreference = $prevEap
    }
    if (-not $smokeFailed) {
        throw "smoke-plugin-echo-php should fail when plugin physically disconnected"
    }
    Write-Host "  [OK] smoke fails when plugin physically disconnected" -ForegroundColor DarkGray

    Write-Host "=== plugin physical: start echo-php / verify reconnect ===" -ForegroundColor Cyan
    $startScript = @'
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml up -d echo-php
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:8088/health >/dev/null 2>&1; then
    echo "[OK] echo-php healthy on guest"
    exit 0
  fi
  sleep 2
done
echo "[FAIL] echo-php health timeout"
exit 1
'@
    Invoke-IntegrationsGuest -Script $startScript
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if (Test-PluginHttpReachable "http://127.0.0.1:18088/health") { break }
        Start-Sleep -Seconds 2
    }
    if (-not (Test-PluginHttpReachable "http://127.0.0.1:18088/health")) {
        throw "host :18088 not healthy after echo-php start"
    }

    & (Join-Path $Root "scripts\smoke-plugin-echo-php.ps1") -BaseUrl "http://127.0.0.1:18088"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "[OK] plugin lifecycle - all $($bridges.Count) bridges" -ForegroundColor Green
