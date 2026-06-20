#!/bin/sh
set -e

export KORUS_WS_GATEWAY_HOST="${KORUS_WS_GATEWAY_HOST:-host.docker.internal}"
export KORUS_WS_GATEWAY_PORT="${KORUS_WS_GATEWAY_PORT:-8081}"

_api_upstream="${WEB_CLIENT_API_UPSTREAM:-http://host.docker.internal:8080}"
_api_upstream="${_api_upstream#http://}"
_api_upstream="${_api_upstream#https://}"
export WEB_CLIENT_API_UPSTREAM_HOST="${WEB_CLIENT_API_UPSTREAM_HOST:-${_api_upstream%%:*}}"
export WEB_CLIENT_API_UPSTREAM_PORT="${WEB_CLIENT_API_UPSTREAM_PORT:-${_api_upstream##*:}}"

escape_sed() {
  printf '%s' "$1" | sed 's/[&/\]/\\&/g'
}
for _var in KORUS_WS_GATEWAY_HOST KORUS_WS_GATEWAY_PORT WEB_CLIENT_API_UPSTREAM_HOST WEB_CLIENT_API_UPSTREAM_PORT; do
  eval "_val=\$$_var"
  eval "export _escaped_$_var=\$(escape_sed \"\$_val\")"
done

mkdir -p /var/lib/korus
if command -v python3 >/dev/null 2>&1 && [ -f /opt/korus/generate-web-client-env.py ]; then
  python3 /opt/korus/generate-web-client-env.py -o /var/lib/korus/web-client-env.js
else
  echo 'window.__WEB_CLIENT__ = { wsUrl: "ws://127.0.0.1:8081/ws", iceServersJson: null, vapidPublicKey: null, disableServiceWorker: false };' \
    > /var/lib/korus/web-client-env.js
fi

_template="${NGINX_CONF_TEMPLATE:-/etc/nginx/nginx.conf.static.template}"
sed \
  -e "s/\${KORUS_WS_GATEWAY_HOST}/${_escaped_KORUS_WS_GATEWAY_HOST}/g" \
  -e "s/\${KORUS_WS_GATEWAY_PORT}/${_escaped_KORUS_WS_GATEWAY_PORT}/g" \
  -e "s/\${WEB_CLIENT_API_UPSTREAM_HOST}/${_escaped_WEB_CLIENT_API_UPSTREAM_HOST}/g" \
  -e "s/\${WEB_CLIENT_API_UPSTREAM_PORT}/${_escaped_WEB_CLIENT_API_UPSTREAM_PORT}/g" \
  < "$_template" > /etc/nginx/nginx.conf

if ! nginx -t 2>&1; then
  echo "lb-static: nginx -t failed" >&2
  cat /etc/nginx/nginx.conf >&2
  exit 1
fi

exec "$@"
