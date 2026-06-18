#!/usr/bin/env bash
# docker/docker-compose.full-server.yml from repo root:
#   ./scripts/full-stack-up.sh [--build|-b] [--skip-ensure|-S]
# Sets KORUS_* env, ensure tooling, compose with retry.
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
EXPORT_SMOKE=false
EXPORT_AUTO_QUEUE=false
WAIT_READY=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    --export-smoke) EXPORT_SMOKE=true ;;
    --export-auto-queue) EXPORT_AUTO_QUEUE=true; EXPORT_SMOKE=true ;;
    --no-wait-ready) WAIT_READY=0 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S] [--export-smoke] [--export-auto-queue] [--no-wait-ready]"
      echo "  Env SKIP_KORUS_ENSURE=1 also skips install-environment."
      echo "  After success: hints for korus-web (attach, optional Turn), smoke, full-stack-down."
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

if [[ "${KORUS_FLEET_LAB:-0}" == "1" && -z "${FLEET_TARGETS_JSON:-}" ]]; then
  if [[ -f "$ROOT/docker/fleet-targets.qemu.json" ]]; then
    export FLEET_TARGETS_JSON="$(tr -d '\n\r' < "$ROOT/docker/fleet-targets.qemu.json")"
  elif [[ -f "$ROOT/docker/fleet-targets.lab.json" ]]; then
    export FLEET_TARGETS_JSON="$(tr -d '\n\r' < "$ROOT/docker/fleet-targets.lab.json")"
  else
    export FLEET_TARGETS_JSON='[{"id":"ws-gateway","role":"ws-gateway","base_url":"http://ws-gateway:9191","health_path":"/health"},{"id":"message-pipeline","role":"worker","base_url":"http://message-pipeline:9191","health_path":"/health"},{"id":"keycloak","role":"keycloak","base_url":"http://keycloak:8080","health_path":"/realms/avandocmsg"},{"id":"minio","role":"minio","base_url":"http://minio:9000","health_path":"/minio/health/live"},{"id":"nats","role":"nats","base_url":"http://nats:8222","health_path":"/healthz"},{"id":"solr","role":"solr","base_url":"http://solr:8983","health_path":"/solr/admin/info/system?wt=json"}]'
  fi
fi
if [[ "${KORUS_FLEET_LAB:-0}" == "1" ]]; then
  export ORG_IP_ALLOWLIST_ENFORCE="${ORG_IP_ALLOWLIST_ENFORCE:-1}"
fi

COMPOSE="$KORUS_COMPOSE_FULL_SERVER"
if [[ ! -f "$COMPOSE" ]]; then
  echo "Not found: $COMPOSE" >&2
  exit 1
fi

cd "$ROOT"
if [[ -f "$ROOT/docker/.env.livekit" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/docker/.env.livekit"
  set +a
fi
# Limit parallel compose builds by default — avoids OOM on 8–10G RAM hosts during redeploy.
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"
if [ "${KORUS_QEMU_CONSOLE:-0}" = "1" ]; then
  export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
  export COMPOSE_ANSI="${COMPOSE_ANSI:-never}"
fi
echo "cd $ROOT"
compose_args=(-f "$COMPOSE" --profile full --profile push)
while IFS= read -r arg; do
  compose_args+=("$arg")
done < <(korus_fleet_lab_compose_args "$ROOT")
if $EXPORT_SMOKE; then
  while IFS= read -r arg; do
    compose_args+=("$arg")
  done < <(korus_export_smoke_compose_args "$ROOT" "$( $EXPORT_AUTO_QUEUE && echo 1 || echo 0 )")
fi
if $BUILD && [ "${KORUS_QEMU_CONSOLE:-0}" = "1" ]; then
  echo "QEMU: docker compose build with COMPOSE_PARALLEL_LIMIT=${COMPOSE_PARALLEL_LIMIT} (10G RAM — avoid OOM from parallel Gradle)"
  COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT}" korus_compose_file_retry "$COMPOSE" build || exit 1
  compose_args+=(up -d)
else
  compose_args+=(up -d)
  $BUILD && compose_args+=(--build)
fi
echo "docker compose ${compose_args[*]}"
korus_compose_up_multi_retry "${compose_args[@]}" || exit 1
if [[ "${KORUS_FLEET_LAB:-0}" == "1" ]]; then
  echo "Fleet lab: force-recreate core-api (FLEET_TARGETS_JSON env)" >&2
  recycle_args=(-f "$COMPOSE" --profile full --profile push)
  while IFS= read -r arg; do
    recycle_args+=("$arg")
  done < <(korus_fleet_lab_compose_args "$ROOT")
  recycle_args+=(up -d --no-deps --force-recreate core-api)
  korus_compose_up_multi_retry "${recycle_args[@]}" || exit 1
fi

echo ""
if $EXPORT_SMOKE && [[ "$WAIT_READY" == "1" ]]; then
  echo "Waiting for export stack health ..." >&2
  "${SCRIPT_DIR}/wait-stack-ready.sh" || exit 1
fi

echo "[OK] Full stack: core-api :8080, Keycloak :8081, ws-gateway :8082, export-replay :9193, retention :9192, push-worker :9194"
echo "Web Push VAPID: ./scripts/generate-vapid.sh  (then set keys on push-worker + korus-web WEB_CLIENT_VAPID_PUBLIC_KEY)" >&2
echo "Admin: http://localhost:8080/admin/  (realm avandocmsg: csadmin/csadmin or admin/admin)"
echo "Attach korus-web: ./scripts/korus-web-up.sh --attach --build  (PowerShell: .\\scripts\\korus-web-up.ps1 -Attach -Build)"
echo "Optional local TURN with UI: ./scripts/korus-web-up.sh --attach --turn --build" >&2
echo "Smoke korus-web (after UI): ./scripts/smoke-korus-web.sh --check-api" >&2
echo "Stop full stack: ./scripts/full-stack-down.sh  (then korus-web-down if UI was up)"
if $EXPORT_SMOKE; then
  echo "Export pack: ./scripts/smoke-export-compliance-pack.sh" >&2
  echo "  one-shot: ./scripts/smoke-export-compliance-stack.sh --auto-queue [--down]" >&2
fi
