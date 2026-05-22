# Shared for bash start/full-stack: KORUS_* env, ensure tooling, compose with retry.
# shellcheck shell=bash
# Usage: SCRIPT_DIR=...; source "$SCRIPT_DIR/lib/korus-env.sh"; korus_set_path_env "$ROOT"; korus_ensure_env "$ROOT"

# Prints compose -f args for export smoke overlays (use after korus_set_path_env).
korus_export_smoke_compose_args() {
  local root="$1"
  local auto="${2:-0}"
  printf '%s\n' \
    -f "$root/docker/docker-compose.export-smoke.yml" \
    -f "$root/docker/docker-compose.retention-export-smoke.yml"
  if [[ "$auto" == "1" ]]; then
    printf '%s\n' -f "$root/docker/docker-compose.export-auto-queue-smoke.yml"
  fi
}

korus_set_path_env() {
  local root="$1"
  export KORUS_REPO_ROOT="$root"
  export KORUS_DOCKER_DIR="$root/docker"
  export KORUS_COMPOSE_DEV_MIN="$root/docker/docker-compose.dev-min.yml"
  export KORUS_COMPOSE_FULL_SERVER="$root/docker/docker-compose.full-server.yml"
  export KORUS_COMPOSE_LAN_PUBLISH="$root/docker/docker-compose.lan-publish.yml"
  export KORUS_DEV_OVERLAY_DIR="$root/dev-overlay"
  export KORUS_SCRIPTS_DIR="$root/scripts"
  export KORUS_KORUS_WEB_DIR="$root/korus-web"
  export KORUS_KORUS_WEB_COMPOSE="$root/korus-web/docker-compose.yml"
  export KORUS_KORUS_WEB_COMPOSE_ATTACH="$root/korus-web/docker-compose.attach.yml"
  export KORUS_KORUS_WEB_COMPOSE_TURN="$root/korus-web/docker-compose.turn.yml"
  export KORUS_KORUS_WEB_COMPOSE_HOTSWAP="$root/korus-web/docker-compose.hotswap.yml"
}

korus_ensure_env() {
  local root="$1"
  if bash "$root/scripts/install-environment.sh" --quiet; then
    return 0
  fi
  if [[ -f "$root/scripts/install-env-silent.sh" ]]; then
    bash "$root/scripts/install-env-silent.sh" --quiet || true
  fi
  bash "$root/scripts/install-environment.sh" --quiet
}

korus_compose_up_retry() {
  local compose_file="$1"
  shift
  local i
  for i in 1 2; do
    if docker compose -f "$compose_file" "$@"; then
      return 0
    fi
    sleep 10
  done
  return 1
}

# docker compose -f base -f overlay ... up -d
korus_compose_up_multi_retry() {
  local i
  for i in 1 2; do
    if docker compose "$@"; then
      return 0
    fi
    sleep 10
  done
  return 1
}

# docker compose from a working dir (e.g. korus-web with relative --env-file .env)
korus_compose_in_dir_retry() {
  local dir="$1"
  shift
  local i
  for i in 1 2; do
    if ( cd "$dir" && docker "$@" ); then
      return 0
    fi
    sleep 10
  done
  return 1
}

# docker compose -f FILE ... (pull, build, down, etc.)
korus_compose_file_retry() {
  local compose_file="$1"
  shift
  local i
  for i in 1 2; do
    if docker compose -f "$compose_file" "$@"; then
      return 0
    fi
    sleep 10
  done
  return 1
}
