#!/bin/sh
set -e
export KORUS_WS_GATEWAY_HOST="${KORUS_WS_GATEWAY_HOST:-host.docker.internal}"
export KORUS_WS_GATEWAY_PORT="${KORUS_WS_GATEWAY_PORT:-8081}"
envsubst '$KORUS_WS_GATEWAY_HOST $KORUS_WS_GATEWAY_PORT' \
  < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
exec "$@"
