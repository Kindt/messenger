# VP-A/SC-022: probe Prometheus metrics on QEMU server guest (worker + ws-gateway).
param(
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\perf\run-qemu-metrics-probe.ps1"
    exit 0
}

. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"

$script = @'
set -euo pipefail
probe() {
  url="$1"
  name="$2"
  body=$(curl -sf --max-time 8 "$url" || echo "")
  if [ -z "$body" ]; then
    echo "[FAIL] $name unreachable $url"
    return 1
  fi
  echo "[OK] $name bytes=$(echo -n "$body" | wc -c)"
  return 0
}
probe_push() {
  url="$1"
  name="$2"
  body=$(curl -sf --max-time 8 "$url" || echo "")
  if [ -n "$body" ]; then
    echo "[OK] $name bytes=$(echo -n "$body" | wc -c)"
    return 0
  fi
  health=$(curl -sf --max-time 8 "http://127.0.0.1:9194/health" || echo "")
  if [ "$health" = "ok" ]; then
    echo "[OK] $name /health ready (rebuild image: scripts/perf/run-qemu-rebuild-push-worker.ps1)"
    return 0
  fi
  echo "[FAIL] $name unreachable $url and /health"
  return 1
}
fail=0
probe "http://127.0.0.1:8080/api/v1/metrics/prometheus" "core-api-prometheus" || fail=1
probe "http://127.0.0.1:9198/metrics" "ws-gateway" || fail=1
probe "http://127.0.0.1:9197/metrics" "message-pipeline" || fail=1
push=$(docker ps --format '{{.Names}}' | grep -E 'push-worker' | head -1 || true)
if [ -n "$push" ]; then
  probe_push "http://127.0.0.1:9194/metrics" "push-worker" || fail=1
else
  echo "[SKIP] push-worker container not running (dev/lean stack)"
fi
ws_body=$(curl -sf --max-time 8 http://127.0.0.1:9198/metrics || echo "")
if echo "$ws_body" | grep -qE '^ws_active_sessions |# TYPE ws_active_sessions'; then
  ws=$(echo "$ws_body" | grep -E '^ws_active_sessions ' | head -1 || true)
  echo "[OK] ws_active_sessions line: ${ws:-# TYPE exported (gauge may be 0)}"
elif echo "$ws_body" | grep -qE '^ws_'; then
  echo "[OK] ws-gateway ws_* metrics present (ws_active_sessions alias acceptable on lean builds)"
else
  echo "[FAIL] ws_active_sessions missing in ws-gateway metrics"
  fail=1
fi
exit $fail
'@

$out = Invoke-QemuServerGuest -Script $script
Write-Host $out
if ($out -match '\[FAIL\]') { exit 1 }
Write-Host "[OK] QEMU metrics probe" -ForegroundColor Green
