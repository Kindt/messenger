#!/bin/sh
set -e

export KORUS_WS_GATEWAY_HOST="${KORUS_WS_GATEWAY_HOST:-host.docker.internal}"
export KORUS_WS_GATEWAY_PORT="${KORUS_WS_GATEWAY_PORT:-8081}"

# Подставляем только ws-gateway host/port. Не envsubst: иначе ломаются nginx $host, $remote_addr и т.д.
escape_sed() {
  printf '%s' "$1" | sed 's/[&/\]/\\&/g'
}
_gw_host=$(escape_sed "$KORUS_WS_GATEWAY_HOST")
_gw_port=$(escape_sed "$KORUS_WS_GATEWAY_PORT")

sed \
  -e "s/\${KORUS_WS_GATEWAY_HOST}/${_gw_host}/g" \
  -e "s/\${KORUS_WS_GATEWAY_PORT}/${_gw_port}/g" \
  < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

if ! nginx -t 2>&1; then
  echo "lb: nginx -t failed; generated config:" >&2
  cat /etc/nginx/nginx.conf >&2
  exit 1
fi

exec "$@"
