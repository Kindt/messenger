#!/usr/bin/env bash
# Shared helpers for WAR smokes on QEMU server guest (spec 021).
set -euo pipefail

war_smoke_compose_network() {
  local cid=""
  cid=$(docker ps -q -f name=core-api 2>/dev/null | head -1 || true)
  if [ -z "$cid" ]; then
    cid=$(docker ps -q -f name=postgres-hot 2>/dev/null | head -1 || true)
  fi
  if [ -n "$cid" ]; then
    docker inspect "$cid" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}'
    return 0
  fi
  echo "korus_messenger_dev_min"
}

war_smoke_wait_http() {
  local url="$1"
  local timeout="${2:-180}"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}
