#Requires -Version 5.1
# VPP-2 physical: docker workers + KORUS_PRODUCT_ADDONS toggle (spec 030). No SKIP.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$ServerSshPort = 12221,
    [switch]$SkipEnvToggle,
    [switch]$SkipWorkerToggle,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-module-lifecycle-physical.ps1

Physical connect/disconnect on QEMU server guest:
  - docker stop/start: push-worker, preview-worker, retention-worker, export-replay-worker, bot-delivery-worker
  - KORUS_PRODUCT_ADDONS remove addon-productivity + core-api recreate + restore

Requires: qemu-up server guest SSH :12221
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $Root "scripts\perf\lib\Invoke-QemuServerGuest.ps1")

$API = "$BaseUrl/api/v1"

function Test-ApiAddonSelected([string]$AddonId) {
    $cap = Invoke-RestMethod -Method GET -Uri "$API/platform/capabilities" -TimeoutSec 15
    $mod = $cap.modules.$AddonId
    if (-not $mod) { return $false }
    return [bool]$mod.selected
}

function Wait-GuestHealth {
    param([int]$MaxSec = 600)
    $deadline = (Get-Date).AddSeconds($MaxSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 5
    }
    return $false
}

function Invoke-WorkerPhysicalToggle {
    param(
        [string]$Name,
        [string]$Filter,
        [int]$GuestHealthPort = 0,
        [string]$AddonId = "",
        [string]$LogPattern = ""
    )
    Write-Host ""
    Write-Host "=== physical worker: $Name ===" -ForegroundColor Cyan
    if ($GuestHealthPort -gt 0) {
        $healthProbe = "curl -sf http://127.0.0.1:$GuestHealthPort/health >/dev/null || curl -sf http://127.0.0.1:$GuestHealthPort/metrics >/dev/null"
    } elseif ($LogPattern) {
        $healthProbe = "docker logs `$cid 2>&1 | tail -n 80 | grep -q '$LogPattern'"
    } else {
        $healthProbe = "docker inspect -f '{{.State.Running}}' `$cid | grep -q true"
    }
    $script = @"
set -e
cid=`$(docker ps -q --filter name=$Filter | head -1)
if [ -z "`$cid" ]; then
  echo "[FAIL] $Name container not running (filter=$Filter)"
  exit 1
fi
echo container=`$cid
eval "$healthProbe" || { echo "[FAIL] $Name probe before stop"; exit 1; }
docker stop "`$cid"
sleep 2
if docker ps -q --filter id=`$cid | grep -q .; then
  echo "[FAIL] $Name still running after stop"
  exit 1
fi
echo "[OK] $Name stopped"
docker start "`$cid"
for i in `$(seq 1 30); do
  cid=`$(docker ps -q --filter name=$Filter | head -1)
  if [ -n "`$cid" ] && eval "$healthProbe"; then
    echo "[OK] $Name healthy after start"
    exit 0
  fi
  sleep 2
done
echo "[FAIL] $Name recovery timeout after start"
exit 1
"@
    Invoke-QemuServerGuest -Script $script
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($AddonId -and -not (Test-ApiAddonSelected $AddonId)) {
        throw "addon $AddonId not selected after $Name physical toggle"
    }
    Write-Host "  [OK] $Name stop/start" -ForegroundColor Green
}

if (-not $SkipWorkerToggle) {
    $workers = @(
        @{ name = "push-worker"; filter = "push-worker"; port = 9194; addon = "addon-engage" },
        @{ name = "preview-worker"; filter = "preview-worker"; port = 9195; addon = "addon-engage" },
        @{ name = "retention-worker"; filter = "retention-worker"; port = 9192; addon = "addon-retention" },
        @{ name = "export-replay-worker"; filter = "export-replay-worker"; port = 9193; addon = "addon-export" },
        @{ name = "bot-delivery-worker"; filter = "bot-delivery-worker"; port = 0; addon = "addon-bots"; log = "bot-delivery-workers" },
        @{ name = "archiver-worker"; filter = "archiver-worker"; port = 0; addon = "addon-archive" },
        @{ name = "deep-archiver-worker"; filter = "deep-archiver-worker"; port = 9196; addon = "addon-deep-archive" },
        @{ name = "indexer-worker"; filter = "indexer-worker"; port = 9197; addon = "addon-search" }
    )
    foreach ($w in $workers) {
        Invoke-WorkerPhysicalToggle -Name $w.name -Filter $w.filter -GuestHealthPort $w.port -AddonId $w.addon -LogPattern $w.log
    }
}

if (-not $SkipEnvToggle) {
    Write-Host ""
    Write-Host "=== physical: KORUS_PRODUCT_ADDONS deselect addon-productivity ===" -ForegroundColor Cyan
    if (-not (Test-ApiAddonSelected "addon-productivity")) {
        throw "addon-productivity not selected - run qemu-enable-regression-addons.ps1 before physical env toggle"
    }

    $fullAddons = "addon-productivity,addon-engage,addon-search,addon-collaboration,addon-ai,addon-live,addon-retention,addon-archive,addon-deep-archive,addon-export,addon-enterprise-auth,addon-e2ee,addon-bots,addon-integrations,addon-federation,addon-dlp,addon-migration-import"
    $reducedAddons = ($fullAddons -split "," | Where-Object { $_ -ne "addon-productivity" }) -join ","

    $toggleScript = @"
set -e
cd /mnt/korus
python3 - <<'PY'
from pathlib import Path
addons = "$reducedAddons"
fleet = Path("docker/fleet-targets.qemu.json").read_text(encoding="utf-8").strip()
Path("/tmp/korus-qemu-regress.env").write_text(
    f"FLEET_TARGETS_JSON={fleet}\n"
    "FLEET_AGGREGATOR_NODE=core-api@qemu-server\n"
    f"KORUS_PRODUCT_ADDONS={addons}\n"
    "SCIM_BEARER_TOKEN=korus-scim-lab-demo\n",
    encoding="utf-8",
)
PY
sudo docker compose --env-file /tmp/korus-qemu-regress.env \
  -f docker/docker-compose.full-server.yml \
  -f docker/docker-compose.fleet-lab.yml \
  -f docker/docker-compose.qemu-regression-lab.yml \
  up -d --force-recreate core-api
for i in `$(seq 1 40); do
  code=`$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/health)
  echo health=`$code
  [ "`$code" = "200" ] && exit 0
  sleep 5
done
exit 1
"@
    Invoke-QemuServerGuest -Script $toggleScript
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (-not (Wait-GuestHealth)) { throw "host API health timeout after env toggle" }

    $cap = Invoke-RestMethod -Method GET -Uri "$API/platform/capabilities"
    $prod = $cap.modules.'addon-productivity'
    if ($prod.selected -eq $true) {
        throw "physical deselect failed: addon-productivity still selected in capabilities"
    }
    Write-Host "  [OK] addon-productivity physically deselected" -ForegroundColor DarkGray

    try {
        $login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
            -Body '{"username":"csadmin","password":"csadmin"}'
        $h = @{ Authorization = "Bearer $($login.access_token)" }
        Invoke-WebRequest -Method GET -Uri "$API/productivity/polls" -Headers $h -UseBasicParsing -TimeoutSec 10 | Out-Null
        $code = 200
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    }
    if ($code -ne 503 -and $code -ne 404) {
        throw "expected 503/404 on productivity API after physical deselect, got $code"
    }
    Write-Host "  [OK] productivity API gated (HTTP $code)" -ForegroundColor DarkGray

    Write-Host ""
    Write-Host "=== physical: restore full regression addons ===" -ForegroundColor Cyan
    & (Join-Path $Root "scripts\qemu-enable-regression-addons.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (-not (Wait-GuestHealth)) { throw "host API health timeout after restore" }
    $waitApi = Join-Path $Root "deploy\qemu\run\wait-api-health.ps1"
    if (Test-Path $waitApi) {
        & $waitApi -MaxMinutes 10
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    if (-not (Test-ApiAddonSelected "addon-productivity")) {
        throw "addon-productivity not restored after qemu-enable-regression-addons"
    }
}

Write-Host ""
Write-Host "=== physical: restore integrations VM after core-api recreate ===" -ForegroundColor Cyan
& (Join-Path $Root "scripts\vpp\Wait-IntegrationsOnline.ps1") -MaxSec 900 -StartVmIfDown -RepairGateway
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "[OK] physical module lifecycle (workers + env toggle)" -ForegroundColor Green
