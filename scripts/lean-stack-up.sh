#!/usr/bin/env bash
# Lean stack (spec 006 FR-OPT-01): full-server + pilot-overrides + keycloak-prod.
# Base-only product addons (legacy korus_deploy_profile=pilot). Run in QEMU server guest.
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
DOWN_FULL_FIRST=false
PROFILES=()
WAIT_READY=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    --down-full-first) DOWN_FULL_FIRST=true ;;
    --profile) PROFILES+=("$2"); shift ;;
    --no-wait-ready) WAIT_READY=0 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S] [--down-full-first] [--profile NAME]... [--no-wait-ready]"
      echo "  Lean prod stack (no Solr/ZK). Profiles: push, retention, compliance, archive"
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

COMPOSE_BASE="$KORUS_COMPOSE_FULL_SERVER"
COMPOSE_LEAN_OVERRIDES="$KORUS_DOCKER_DIR/docker-compose.pilot-overrides.yml"
COMPOSE_KC_PROD="$KORUS_COMPOSE_KEYCLOAK_PROD"
COMPOSE_RESOURCE_LIMITS="$KORUS_DOCKER_DIR/docker-compose.resource-limits.yml"
if [[ ! -f "$COMPOSE_BASE" ]]; then
  echo "Not found: $COMPOSE_BASE" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_LEAN_OVERRIDES" ]]; then
  echo "Not found: $COMPOSE_LEAN_OVERRIDES" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_KC_PROD" ]]; then
  echo "Not found: $COMPOSE_KC_PROD" >&2
  exit 1
fi

cd "$ROOT"
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"
if [[ "${KORUS_QEMU_CONSOLE:-0}" = "1" ]]; then
  export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
  export COMPOSE_ANSI="${COMPOSE_ANSI:-never}"
fi

if $DOWN_FULL_FIRST && [[ -f "$KORUS_COMPOSE_FULL_SERVER" ]]; then
  echo "Stopping full-server stack (best effort)..." >&2
  docker compose -f "$KORUS_COMPOSE_FULL_SERVER" down --remove-orphans 2>/dev/null || true
fi

compose_args=(-f "$COMPOSE_BASE" -f "$COMPOSE_LEAN_OVERRIDES" -f "$COMPOSE_KC_PROD" -f "$COMPOSE_RESOURCE_LIMITS")
for profile in "${PROFILES[@]}"; do
  compose_args+=(--profile "$profile")
done

echo "cd $ROOT"
if $BUILD && [[ "${KORUS_QEMU_CONSOLE:-0}" = "1" ]]; then
  echo "QEMU: docker compose build lean stack (COMPOSE_PARALLEL_LIMIT=${COMPOSE_PARALLEL_LIMIT})" >&2
  COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT}" korus_compose_up_multi_retry "${compose_args[@]}" build || exit 1
  compose_args+=(up -d)
else
  compose_args+=(up -d)
  $BUILD && compose_args+=(--build)
fi
echo "docker compose ${compose_args[*]}"
korus_compose_up_multi_retry "${compose_args[@]}" || exit 1

if [[ "$WAIT_READY" == "1" ]]; then
  BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
  KEYCLOAK_URL="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
  TIMEOUT_SEC="${TIMEOUT_SEC:-420}"
  deadline=$((SECONDS + TIMEOUT_SEC))
  core_ok=false
  kc_ok=false

  while (( SECONDS < deadline )); do
    if ! $core_ok && curl -fsS --max-time 5 "${BASE_URL}/api/v1/health" >/dev/null 2>&1 \
      && curl -fsS --max-time 5 "${BASE_URL}/api/v1/health/ready" >/dev/null 2>&1; then
      echo "[OK] core-api health + ready" >&2
      core_ok=true
    fi
    if ! $kc_ok && curl -fsS --max-time 5 "${KEYCLOAK_URL}/health/ready" >/dev/null 2>&1; then
      echo "[OK] keycloak health/ready" >&2
      kc_ok=true
    fi
    if $core_ok && $kc_ok; then
      break
    fi
    echo "  waiting core=$core_ok keycloak=$kc_ok ..." >&2
    sleep 5
  done

  if ! $core_ok || ! $kc_ok; then
    echo "[FAIL] lean stack not ready (core=$core_ok keycloak=$kc_ok)" >&2
    exit 1
  fi
fi

echo "[OK] Lean stack: core-api :8080, Keycloak :8081 (prod), ws-gateway :8082"
echo "Smoke: ./scripts/smoke-lean-stack.sh"
echo "Profiles: --profile push | retention | compliance | archive"
