#!/usr/bin/env bash
# WS soak load (PS-4.1) — run inside server guest or Linux CI against local stack.
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
WEB_BASE="${WEB_BASE:-http://127.0.0.1:9088}"
METRICS_URL="${METRICS_URL:-http://127.0.0.1:9198/metrics}"
USER="${SMOKE_USER:-csadmin}"
PASS="${SMOKE_USER_PASS:-csadmin}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION_SEC="${DURATION_SEC:-300}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/SmokeMessaging.sh
source "$SCRIPT_DIR/lib/SmokeMessaging.sh"

smoke_step "Login"
TOKEN=$(smoke_login "$BASE_URL" "$USER" "$PASS")

WS_BASE="${WS_BASE:-}"
if [[ -z "$WS_BASE" ]]; then
  if curl -sf "$WEB_BASE/web-client-env.js" -o /tmp/korus-ws-env.js 2>/dev/null; then
    WS_BASE=$(python3 - <<'PY'
import re
text=open("/tmp/korus-ws-env.js",encoding="utf-8").read()
m=re.search(r'wsUrl\s*:\s*"([^"]+)"', text)
print(m.group(1).rstrip("/") if m else "ws://127.0.0.1:8082/ws")
PY
)
  else
    WS_BASE="ws://127.0.0.1:8082/ws"
  fi
fi

smoke_step "Open $CONNECTIONS WS connections (python websocket)"
export WS_ORIGIN="${WS_ORIGIN:-http://127.0.0.1:19088}"
python3 - "$WS_BASE" "$TOKEN" "$CONNECTIONS" "$DURATION_SEC" "$METRICS_URL" <<'PY'
import json, os, sys, threading, time, urllib.request, urllib.parse
try:
    import websocket
except ImportError:
    print("[FAIL] pip install websocket-client", file=sys.stderr)
    sys.exit(1)

ws_base, token, conn_s, dur_s, metrics_url = sys.argv[1:6]
connections = int(conn_s)
duration = int(dur_s)
origin = os.environ.get("WS_ORIGIN", "http://127.0.0.1:19088")
url = f"{ws_base}?token={urllib.parse.quote(token)}"

sockets = []
for i in range(connections):
    ws = websocket.create_connection(
        url,
        timeout=15,
        origin=os.environ.get("WS_ORIGIN", "http://127.0.0.1:19088"),
    )
    sockets.append(ws)
    if (i + 1) % 25 == 0:
        print(f"  connected {i+1}/{connections}", flush=True)
print(f"[OK] opened {len(sockets)} connections")

end = time.time() + duration
while time.time() < end:
    time.sleep(30)
    try:
        with urllib.request.urlopen(metrics_url, timeout=5) as resp:
            body = resp.read().decode("utf-8", errors="replace")
        for line in body.splitlines():
            if line.startswith("ws_open_sessions "):
                print(f"  {line.strip()}", flush=True)
                break
    except Exception as e:
        print(f"  [WARN] metrics: {e}", flush=True)

for ws in sockets:
    try:
        ws.close()
    except Exception:
        pass
print(f"[OK] load-ws-soak complete ({connections} conn, {duration}s)")
PY
