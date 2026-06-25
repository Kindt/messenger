set -euo pipefail
docker ps -a --format 'table {{.Names}}\t{{.Status}}' | head -25
cid=$(docker ps -aq --filter name=core-api | head -1)
if [ -n "$cid" ]; then
  echo "--- core-api logs ---"
  docker logs --tail 50 "$cid" 2>&1 | tail -50
fi
curl -sS -m 5 -o /dev/null -w 'guest8080:%{http_code}\n' http://127.0.0.1:8080/api/v1/health || true
grep -E 'KORUS_PRODUCT|PRODUCT_ADDON' /mnt/korus/docker/.env.korus-server /mnt/korus/docker/.env 2>/dev/null || true
docker inspect docker-core-api-1 --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep -iE 'PRODUCT|DEPLOY_PROFILE' || true
