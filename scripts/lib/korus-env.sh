# Shared for bash start/full-stack: KORUS_* env, ensure tooling, compose with retry.
# shellcheck shell=bash
# Usage: SCRIPT_DIR=...; source "$SCRIPT_DIR/lib/korus-env.sh"; korus_set_path_env "$ROOT"; korus_ensure_env "$ROOT"

korus_set_path_env() {
  local root="$1"
  export KORUS_REPO_ROOT="$root"
  export KORUS_DOCKER_DIR="$root/docker"
  export KORUS_COMPOSE_DEV_MIN="$root/docker/docker-compose.dev-min.yml"
  export KORUS_COMPOSE_FULL_SERVER="$root/docker/docker-compose.full-server.yml"
  export KORUS_SCRIPTS_DIR="$root/scripts"
  export KORUS_KORUS_WEB_DIR="$root/korus-web"
  export KORUS_KORUS_WEB_COMPOSE="$root/korus-web/docker-compose.yml"
  export KORUS_KORUS_WEB_COMPOSE_ATTACH="$root/korus-web/docker-compose.attach.yml"
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
