set -euo pipefail
cid=$(docker ps -q --filter name=core-api | head -1)
if [ -n "$cid" ]; then
  docker logs "$cid" 2>&1 | sed -n '170,230p'
fi
