#!/usr/bin/env bash
# Смок стека korus-web (lb → web-client). Нужен curl.
# Использование: ./scripts/smoke-korus-web.sh [--url URL] [--check-api]
#   или задайте WEB_BASE_URL (по умолчанию http://localhost:9088; QEMU web-VM: http://127.0.0.1:19088).
set -euo pipefail

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

WEB_BASE_URL="${WEB_BASE_URL:-http://localhost:9088}"
CHECK_API=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-api|-c) CHECK_API=true; shift ;;
    --url|-u)
      [[ $# -ge 2 ]] || fail "--url requires a value"
      WEB_BASE_URL="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--url|-u <base>] [--check-api|-c]"
      echo "Env: WEB_BASE_URL (default http://localhost:9088)"
      exit 0
      ;;
    *)
      fail "Unknown option: $1 (try --help)"
      ;;
  esac
done

echo "GET $WEB_BASE_URL/health ..." >&2
body=$(curl -fsS "$WEB_BASE_URL/health") || fail "health request"
[[ "$(echo -n "$body" | tr -d '\r\n')" == "ok" ]] || fail "health body expected ok, got: $body"

echo "GET $WEB_BASE_URL/ ..." >&2
html=$(curl -fsS "$WEB_BASE_URL/") || fail "root request"
echo "$html" | grep -q "Korus Messenger" || fail "root HTML missing title marker"

echo "GET $WEB_BASE_URL/web-client-env.js ..." >&2
js=$(curl -fsS "$WEB_BASE_URL/web-client-env.js") || fail "web-client-env.js request"
echo "$js" | grep -q "__WEB_CLIENT__" || fail "web-client-env.js missing __WEB_CLIENT__"
echo "$js" | grep -qE 'wsUrl\s*:' || fail "web-client-env.js missing wsUrl"
echo "$js" | grep -qE 'iceServersJson\s*:' || fail "web-client-env.js missing iceServersJson"
echo "$js" | grep -qE 'iceServersJson\s*:\s*(null|")' || fail "web-client-env.js iceServersJson must be null or a JSON string"
echo "$js" | grep -qE 'vapidPublicKey\s*:' || fail "web-client-env.js missing vapidPublicKey"

if "$CHECK_API"; then
  echo "GET $WEB_BASE_URL/api/v1/health ..." >&2
  json=$(curl -fsS "$WEB_BASE_URL/api/v1/health") || fail "API via proxy"
  echo "$json" | grep -q '"status"' || fail "API health JSON missing status"
fi

if "$CHECK_API"; then
  echo "[OK] korus-web smoke ($WEB_BASE_URL) (+ API proxy)"
else
  echo "[OK] korus-web smoke ($WEB_BASE_URL)"
fi
