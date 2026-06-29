#!/bin/sh
set -e

export KORUS_WS_GATEWAY_HOST="${KORUS_WS_GATEWAY_HOST:-host.docker.internal}"
export KORUS_WS_GATEWAY_PORT="${KORUS_WS_GATEWAY_PORT:-8081}"

_api_upstream="${WEB_CLIENT_API_UPSTREAM:-http://host.docker.internal:8080}"
_api_upstream="${_api_upstream#http://}"
_api_upstream="${_api_upstream#https://}"
case "$_api_upstream" in
  *:*)
    export WEB_CLIENT_API_UPSTREAM_HOST="${WEB_CLIENT_API_UPSTREAM_HOST:-${_api_upstream%%:*}}"
    export WEB_CLIENT_API_UPSTREAM_PORT="${WEB_CLIENT_API_UPSTREAM_PORT:-${_api_upstream##*:}}"
    ;;
  *)
    export WEB_CLIENT_API_UPSTREAM_HOST="${WEB_CLIENT_API_UPSTREAM_HOST:-$_api_upstream}"
    export WEB_CLIENT_API_UPSTREAM_PORT="${WEB_CLIENT_API_UPSTREAM_PORT:-8080}"
    ;;
esac

escape_sed() {
  printf '%s' "$1" | sed 's/[&/\]/\\&/g'
}
for _var in KORUS_WS_GATEWAY_HOST KORUS_WS_GATEWAY_PORT WEB_CLIENT_API_UPSTREAM_HOST WEB_CLIENT_API_UPSTREAM_PORT; do
  eval "_val=\$$_var"
  eval "export _escaped_$_var=\$(escape_sed \"\$_val\")"
done

json_quote() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g' | sed 's/^/"/; s/$/"/'
}

write_web_client_env_js() {
  _ws_url="${WEB_CLIENT_WS_PUBLIC_URL:-ws://127.0.0.1:8081/ws}"
  _ws_url="${_ws_url%/}"
  _ice_raw="${WEB_CLIENT_RTC_ICE_SERVERS:-}"
  _vapid_raw="${WEB_CLIENT_VAPID_PUBLIC_KEY:-}"
  _disable_sw="${WEB_CLIENT_DISABLE_SW:-}"

  if [ -n "$(printf '%s' "$_ice_raw" | tr -d '[:space:]')" ]; then
    _ice_js="$(json_quote "$_ice_raw")"
  else
    _ice_js="null"
  fi
  if [ -n "$(printf '%s' "$_vapid_raw" | tr -d '[:space:]')" ]; then
    _vapid_js="$(json_quote "$_vapid_raw")"
  else
    _vapid_js="null"
  fi
  case "$_disable_sw" in
    1|true|TRUE|True) _sw_js="true" ;;
    *) _sw_js="false" ;;
  esac
  printf 'window.__WEB_CLIENT__ = { wsUrl: %s, iceServersJson: %s, vapidPublicKey: %s, disableServiceWorker: %s };\n' \
    "$(json_quote "$_ws_url")" "$_ice_js" "$_vapid_js" "$_sw_js" \
    > /var/lib/korus/web-client-env.js
}

mkdir -p /var/lib/korus
if command -v python3 >/dev/null 2>&1 && [ -f /opt/korus/generate-web-client-env.py ]; then
  python3 /opt/korus/generate-web-client-env.py -o /var/lib/korus/web-client-env.js
else
  write_web_client_env_js
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
