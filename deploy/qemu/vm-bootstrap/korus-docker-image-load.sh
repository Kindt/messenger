#!/bin/sh
# Load host-prepared docker-base-images.tar (HTTP 10.0.2.2) to skip slow guest pulls.
set -eu

LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"
STAMP_DIR=/var/lib/korus
HOST_GW="${KORUS_QEMU_HOST_GW:-10.0.2.2}"
HTTP_PORT="${KORUS_QEMU_REPO_HTTP_PORT:-18890}"
CACHE_URL="http://${HOST_GW}:${HTTP_PORT}/docker-base-images.tar"
CACHE_TMP="${TMPDIR:-/tmp}/docker-base-images.tar"
STAMP="$STAMP_DIR/docker-cache-loaded"
MAX_ATTEMPTS="${KORUS_DOCKER_CACHE_ATTEMPTS:-48}"
SLEEP_SEC="${KORUS_DOCKER_CACHE_SLEEP:-10}"

log() {
  echo "$1" >>"$LOG" 2>/dev/null || true
}

have_temurin() {
  docker image inspect eclipse-temurin:25-jre >/dev/null 2>&1
}

if ! command -v docker >/dev/null 2>&1; then
  exit 0
fi

if [ -f "$STAMP" ] && have_temurin; then
  log "korus-docker-image-load: cache already loaded"
  exit 0
fi

log "korus-docker-image-load: waiting for $CACHE_URL (up to $((MAX_ATTEMPTS * SLEEP_SEC))s)"

attempt=0
while [ "$attempt" -lt "$MAX_ATTEMPTS" ]; do
  attempt=$((attempt + 1))
  if curl -fSL -o "$CACHE_TMP" "$CACHE_URL" 2>>"$LOG"; then
    log "korus-docker-image-load: loading images from host tar ($(du -h "$CACHE_TMP" 2>/dev/null | awk '{print $1}'))"
    if docker load -i "$CACHE_TMP" >>"$LOG" 2>&1; then
      mkdir -p "$STAMP_DIR" 2>/dev/null || true
      touch "$STAMP" 2>/dev/null || true
      rm -f "$CACHE_TMP" 2>/dev/null || true
      log "korus-docker-image-load: OK (eclipse-temurin:25-jre=$(have_temurin && echo yes || echo no))"
      exit 0
    fi
    log "korus-docker-image-load: ERROR docker load failed"
    rm -f "$CACHE_TMP" 2>/dev/null || true
    exit 1
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$CACHE_URL" 2>/dev/null || echo 000)
  if [ "$code" = "404" ] && [ "$attempt" -ge 3 ]; then
    log "korus-docker-image-load: host tar missing (HTTP 404); guest will pull from registry"
    rm -f "$CACHE_TMP" 2>/dev/null || true
    exit 0
  fi
  sleep "$SLEEP_SEC"
done

log "korus-docker-image-load: host tar not ready; guest will pull from registry"
rm -f "$CACHE_TMP" 2>/dev/null || true
exit 0
