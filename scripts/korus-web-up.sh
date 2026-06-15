#!/usr/bin/env bash
# korus-web stack. From repo root: ./scripts/korus-web-up.sh [--attach|-a] [--turn|-t] [--turn-prod] [--build|-b] [--skip-ensure|-S]
set -euo pipefail

ATTACH=false
TURN=false
TURN_PROD=false
BUILD=false
FORCE_RECREATE=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --attach|-a) ATTACH=true ;;
    --turn|-t) TURN=true ;;
    --turn-prod) TURN_PROD=true ;;
    --build|-b) BUILD=true ;;
    --force-recreate|-r) FORCE_RECREATE=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--attach|-a] [--turn|-t] [--turn-prod] [--build|-b] [--force-recreate|-r] [--skip-ensure|-S]"
      echo "  Env SKIP_KORUS_ENSURE=1 also skips install-environment."
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
KW="$ROOT/korus-web"
# shellcheck source=lib/korus-env.sh
source "$SCRIPT_DIR/lib/korus-env.sh"

korus_set_path_env "$ROOT"

if [[ "$SKIP_KORUS_ENSURE" != "1" ]]; then
  korus_ensure_env "$ROOT" || exit 1
fi

if [[ ! -f "$KORUS_KORUS_WEB_COMPOSE" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE" >&2
  exit 1
fi
if "$ATTACH" && [[ ! -f "$KORUS_KORUS_WEB_COMPOSE_ATTACH" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE_ATTACH" >&2
  exit 1
fi
if "$TURN" && [[ ! -f "$KORUS_KORUS_WEB_COMPOSE_TURN" ]]; then
  echo "Not found: $KORUS_KORUS_WEB_COMPOSE_TURN" >&2
  exit 1
fi
TURN_PROD_FILE="$KW/docker-compose.turn-prod.yml"
if "$TURN_PROD" && [[ ! -f "$TURN_PROD_FILE" ]]; then
  echo "Not found: $TURN_PROD_FILE" >&2
  exit 1
fi

if "$ATTACH"; then
  if ! docker network inspect korus_messenger_dev_min >/dev/null 2>&1; then
    echo "Warning: network korus_messenger_dev_min not found. Start dev-min first or set KORUS_DEV_MIN_NETWORK in korus-web/.env" >&2
  fi
fi

args=(compose)
if [[ -f "$KW/.env" ]]; then
  args+=(--env-file .env)
fi
args+=(-f docker-compose.yml)
if "$ATTACH"; then
  args+=(-f docker-compose.attach.yml)
fi
if "$TURN_PROD"; then
  echo "Turn prod: coturn on 3478; ICE from WEB_CLIENT_RTC_ICE_SERVERS in .env (see docker-compose.turn-prod.yml)" >&2
  args+=(-f docker-compose.turn-prod.yml)
elif "$TURN"; then
  echo "Turn: coturn on host 3478/tcp+udp; WEB_CLIENT_RTC_ICE_SERVERS → 127.0.0.1 (see korus-web/docker-compose.turn.yml)" >&2
  args+=(-f docker-compose.turn.yml)
fi
args+=(up -d --remove-orphans)
if "$BUILD"; then
  args+=(--build)
fi
if "$FORCE_RECREATE"; then
  args+=(--force-recreate)
fi

echo "cd $KW" >&2
echo "docker ${args[*]}" >&2
korus_compose_in_dir_retry "$KW" "${args[@]}" || exit 1

echo ""
if "$ATTACH" && { "$TURN" || "$TURN_PROD"; }; then
  echo "[OK] korus-web up (attach + turn)"
elif "$ATTACH"; then
  echo "[OK] korus-web up (attach)"
elif "$TURN_PROD"; then
  echo "[OK] korus-web up (+ turn-prod)"
elif "$TURN"; then
  echo "[OK] korus-web up (+ turn)"
else
  echo "[OK] korus-web up"
fi
if [[ -f "$KW/.env" ]] && ! grep -qE '^[[:space:]]*WEB_CLIENT_VAPID_PUBLIC_KEY=[^[:space:]]' "$KW/.env" 2>/dev/null; then
  echo "Web Push: run ./scripts/generate-vapid.sh and add keys to korus-web/.env + push-worker env" >&2
fi
echo "Smoke: ./scripts/smoke-korus-web.sh --check-api"
echo "Stop:  ./scripts/korus-web-down.sh$(if "$ATTACH"; then echo -n ' --attach'; fi)$(if "$TURN_PROD"; then echo -n ' --turn-prod'; elif "$TURN"; then echo -n ' --turn'; fi)  (same flags as this run)" >&2
echo "Quick check: curl -fsS http://localhost:9088/health"
bash "$ROOT/scripts/dev-ui-hints.sh"
