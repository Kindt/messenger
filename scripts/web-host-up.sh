#!/usr/bin/env bash
# Machine 2: korus-web for LAN (no --attach).
set -euo pipefail

BUILD=false
SKIP_KORUS_ENSURE="${SKIP_KORUS_ENSURE:-0}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build|-b) BUILD=true ;;
    --skip-ensure|-S) SKIP_KORUS_ENSURE=1 ;;
    -h|--help)
      echo "Usage: $0 [--build|-b] [--skip-ensure|-S]"
      echo "  Do not use --attach on a separate host. See deploy/two-host/README.md"
      exit 0
      ;;
    *) echo "Unknown: $1" >&2; exit 1 ;;
  esac
  shift
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KW="$ROOT/korus-web"
ENV_FILE="$KW/.env"

if [[ -f "$ENV_FILE" ]] && grep -qE '^[[:space:]]*KORUS_SERVER_HOST=' "$ENV_FILE"; then
  server_host=$(grep -E '^[[:space:]]*KORUS_SERVER_HOST=' "$ENV_FILE" | tail -1 | sed -E 's/^[[:space:]]*KORUS_SERVER_HOST[[:space:]]*=[[:space:]]*//; s/^["'\'']|["'\'']$//g')
  bad='localhost|127\.0\.0\.1|host\.docker\.internal'
  for key in WEB_CLIENT_API_UPSTREAM WEB_CLIENT_WS_PUBLIC_URL KORUS_WS_GATEWAY_HOST; do
    line=$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" 2>/dev/null || true)
    if [[ -n "$line" ]] && echo "$line" | grep -qiE "$bad"; then
      echo "Warning: two-host: $key should use LAN IP, not localhost/host.docker.internal" >&2
    fi
  done
  ws_line=$(grep -E '^[[:space:]]*WEB_CLIENT_WS_PUBLIC_URL=' "$ENV_FILE" 2>/dev/null | tail -1 || true)
  if [[ -n "$server_host" && -n "$ws_line" ]] && echo "$ws_line" | grep -qF "$server_host"; then
    echo "Warning: WEB_CLIENT_WS_PUBLIC_URL should use WEB machine IP:9088/ws, not server IP ($server_host). Hot-swap without lb: ws://${server_host}:8082/ws" >&2
  fi
else
  echo "Tip: set KORUS_SERVER_HOST in korus-web/.env (deploy/two-host/web.env.example)" >&2
fi

kw_args=()
if "$BUILD"; then kw_args+=(--build); fi
if [[ "$SKIP_KORUS_ENSURE" == "1" ]]; then kw_args+=(--skip-ensure); fi
bash "$SCRIPT_DIR/korus-web-up.sh" "${kw_args[@]}"

echo ""
echo "Two-host: open http://<WEB_LAN_IP>:9088/ from other PCs. Hot-swap: ./scripts/dev-overlay-up.sh"
