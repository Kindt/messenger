#!/usr/bin/env bash
# Post-Ansible deploy acceptance pack (spec 003).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
WS_URL="${WS_URL:-ws://127.0.0.1:8082/ws}"
WEB_BASE_URL="${WEB_BASE_URL:-http://127.0.0.1:9088}"
SKIP_KORUS_WEB="${SKIP_KORUS_WEB:-1}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url|-u) BASE_URL="$2"; shift 2 ;;
    --web-url) WEB_BASE_URL="$2"; SKIP_KORUS_WEB=0; shift 2 ;;
    --with-korus-web) SKIP_KORUS_WEB=0; shift ;;
    -h|--help)
      echo "Usage: $0 [--url API] [--web-url WEB] [--with-korus-web]"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
run() {
  echo ""
  echo ">>> $*" >&2
  "$@"
}

export BASE_URL WS_URL
run bash "$SCRIPT_DIR/wait-stack-ready.sh" --url "$BASE_URL"
run bash "$SCRIPT_DIR/smoke-ready.sh" --url "$BASE_URL"
run bash "$SCRIPT_DIR/smoke-auth.sh" --url "$BASE_URL"
run bash "$SCRIPT_DIR/keycloak-ensure-smoke-users.sh"
run bash "$SCRIPT_DIR/smoke-messaging-e2e.sh" --url "$BASE_URL" --ws-url "$WS_URL" --skip-ensure-users
run bash "$SCRIPT_DIR/smoke-web-parity-api.sh" --url "$BASE_URL"

if [[ "$SKIP_KORUS_WEB" != "1" ]]; then
  export WEB_BASE_URL
  run bash "$SCRIPT_DIR/smoke-korus-web.sh" --check-api
fi

echo ""
echo "[OK] smoke-deploy-acceptance (spec 003)"
