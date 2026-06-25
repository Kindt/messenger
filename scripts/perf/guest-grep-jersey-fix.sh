set -euo pipefail
echo '=== guest repo grep ==='
grep -n 'Defensive copy\|ResourceConfig(jerseyConfig)\|OpenApiConfig.create' \
  /mnt/korus/modules/core-api/src/main/java/com/avandocmsg/messenger/api/bootstrap/CoreApiComposition.java \
  /mnt/korus/modules/core-api/src/main/java/com/avandocmsg/messenger/api/config/JerseyConfig.java 2>/dev/null || echo 'NOT IN REPO'

echo '=== docker logs not modifiable count ==='
cid=$(docker ps -q --filter name=core-api | head -1)
if [ -n "$cid" ]; then
  docker logs "$cid" 2>&1 | grep -c 'not modifiable' || true
  echo '=== container jar strings ==='
  docker exec "$cid" sh -c 'find /app -name "*.jar" -maxdepth 4 2>/dev/null | head -20'
  docker exec "$cid" sh -c 'for j in $(find /app -name "core-api*.jar" -o -name "*core-api*.jar" 2>/dev/null | head -5); do echo "JAR=$j"; strings "$j" 2>/dev/null | grep -F "OpenApiConfig" | head -3 || echo "no openapi string"; done'
  docker exec "$cid" sh -c 'javap -classpath /app/core-api/lib/core-api.jar -c com.avandocmsg.messenger.api.bootstrap.CoreApiComposition 2>/dev/null | grep -E "ResourceConfig|ServletContainer" | head -15'
fi
