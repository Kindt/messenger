# Restore QEMU lab core-api: clear stale KORUS_PRODUCT_ADDONS + force recreate.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"

$script = @'
set -euo pipefail
cd /mnt/korus/docker
ENV_FILE=.env.korus-server
if [ -f "$ENV_FILE" ]; then
  if grep -q '^KORUS_PRODUCT_ADDONS=' "$ENV_FILE"; then
    sed -i 's/^KORUS_PRODUCT_ADDONS=.*/KORUS_PRODUCT_ADDONS=/' "$ENV_FILE"
  else
    echo KORUS_PRODUCT_ADDONS= >> "$ENV_FILE"
  fi
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi
export KORUS_PRODUCT_ADDONS=
COMPOSE_FILES="-f docker-compose.full-server.yml"
if [ -f docker-compose.qemu-lab.yml ]; then
  COMPOSE_FILES="$COMPOSE_FILES -f docker-compose.qemu-lab.yml"
fi
docker compose $COMPOSE_FILES up -d --force-recreate core-api
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
