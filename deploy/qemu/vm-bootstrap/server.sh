#!/bin/sh
set -eu
REPO=/mnt/korus
LOG=/var/log/korus-bootstrap.log
exec >>"$LOG" 2>&1
echo "=== korus-server bootstrap $(date -Iseconds) ==="

for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if [ -f "$REPO/docker/docker-compose.full-server.yml" ]; then
    break
  fi
  echo "waiting for repo at $REPO..."
  sleep 3
done

if [ ! -f "$REPO/docker/docker-compose.full-server.yml" ]; then
  echo "ERROR: repo not mounted at $REPO"
  exit 1
fi

cd "$REPO"
export COMPOSE_FILE=docker/docker-compose.full-server.yml:docker/docker-compose.lan-publish.yml
docker compose -f docker/docker-compose.full-server.yml -f docker/docker-compose.lan-publish.yml up -d --build
for i in 1 2 3 4 5 6 7 8 9 10 12 14 16 18 20 24 30; do
  if curl -fsS http://127.0.0.1:8080/realms/avandocmsg >/dev/null 2>&1; then
    break
  fi
  echo "waiting for Keycloak on :8080..."
  sleep 5
done
if [ -f "$REPO/scripts/keycloak-ensure-dev-users.sh" ]; then
  KEYCLOAK_URL=http://127.0.0.1:8080 sh "$REPO/scripts/keycloak-ensure-dev-users.sh" || echo "WARN: keycloak-ensure-dev-users failed"
fi
echo "=== server stack up done ==="
