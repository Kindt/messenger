set -euo pipefail
cid=$(docker ps -q --filter name=core-api | head -1)
echo "guest8080=$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/health || echo down)"
echo "not_modifiable_count=$(docker logs "$cid" 2>&1 | grep -c 'not modifiable' || true)"
echo "jakarta_xmlbind_cnf=$(docker logs "$cid" 2>&1 | grep -c JakartaXmlBind || true)"
echo "--- tail ---"
docker logs --tail 8 "$cid" 2>&1
