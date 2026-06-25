# Start lab Prometheus/Grafana on server guest and verify scrape (Wave D, spec 025).
param(
    [switch]$SkipStart,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\perf\run-qemu-observability-lab.ps1 [-SkipStart]"
    exit 0
}

. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"

if ($SkipStart) {
    $script = @'
set -euo pipefail
targets=$(curl -sf --max-time 15 http://127.0.0.1:9090/api/v1/targets || echo "")
if [ -z "$targets" ]; then echo "[FAIL] prometheus :9090 unreachable"; exit 1; fi
echo "[OK] prometheus api reachable"
up=$(echo "$targets" | grep -o '"health":"up"' | wc -l)
echo "[INFO] prometheus targets health=up count=$up"
dash=$(curl -sf -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/)
if [ "$dash" = "200" ] || [ "$dash" = "302" ]; then echo "[OK] grafana :3001 http=$dash"; else echo "[FAIL] grafana http=$dash"; exit 1; fi
'@
} else {
    $script = @'
set -euo pipefail
cd /mnt/korus/deploy/observability
if docker ps --format '{{.Names}}' | grep -q '^observability-prometheus-1$'; then
  echo "[OK] observability stack already running"
else
  docker compose -f docker-compose.observability.yml up -d --quiet-pull >/tmp/korus-obs-up.log 2>&1 || { tail -40 /tmp/korus-obs-up.log; exit 1; }
  echo "[OK] observability stack started"
fi
sleep 10
targets=$(curl -sf --max-time 15 http://127.0.0.1:9090/api/v1/targets || echo "")
if [ -z "$targets" ]; then echo "[FAIL] prometheus :9090 unreachable"; exit 1; fi
echo "[OK] prometheus api reachable"
up=$(echo "$targets" | grep -o '"health":"up"' | wc -l)
echo "[INFO] prometheus targets health=up count=$up"
dash=$(curl -sf -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/)
if [ "$dash" = "200" ] || [ "$dash" = "302" ]; then echo "[OK] grafana :3001 http=$dash"; else echo "[FAIL] grafana http=$dash"; exit 1; fi
'@
}

$out = Invoke-QemuServerGuest -Script $script
Write-Host $out
if ($out -match '\[FAIL\]') { exit 1 }
Write-Host "[OK] QEMU observability lab" -ForegroundColor Green
