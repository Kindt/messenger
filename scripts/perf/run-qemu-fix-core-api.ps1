# Restore QEMU lab core-api health without wiping regression addons.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$enableAddons = Join-Path $Root "scripts\qemu-enable-regression-addons.ps1"
if (Test-Path $enableAddons) {
  & $enableAddons
  exit $LASTEXITCODE
}

# Fallback: minimal recreate (no addon wipe)
. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"

$script = @'
set -euo pipefail
cd /mnt/korus/docker
COMPOSE_ARGS=( -f docker-compose.full-server.yml )
if [ -f docker-compose.fleet-lab.yml ] && [ -f docker-compose.qemu-regression-lab.yml ] && [ -f /tmp/korus-qemu-regress.env ]; then
  COMPOSE_ARGS+=( -f docker-compose.fleet-lab.yml -f docker-compose.qemu-regression-lab.yml --env-file /tmp/korus-qemu-regress.env )
fi
sudo docker compose "${COMPOSE_ARGS[@]}" up -d --force-recreate core-api
for i in $(seq 1 36); do
  code=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/health || true)
  echo "health=$code"
  if [ "$code" = "200" ]; then
    echo "[OK] core-api healthy"
    exit 0
  fi
  sleep 5
done
echo "[FAIL] core-api health timeout"
docker logs --tail 40 $(docker ps -aq --filter name=core-api | head -1) 2>&1 | tail -40
exit 1
'@

Invoke-QemuServerGuest -Script $script
