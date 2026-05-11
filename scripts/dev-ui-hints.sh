#!/usr/bin/env bash
# Подсказки по URL и тестовым учётным данным (локальный dev-стенд).
# Из корня репозитория: ./scripts/dev-ui-hints.sh
set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  echo "Usage: $0"
  echo "  Repo root is fixed (parent of scripts/). Windows: .\\scripts\\dev-ui-hints.ps1 -Help"
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "Unknown argument: $1 (try --help)" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
read_lb_port() {
  local f="$ROOT/korus-web/.env"
  if [[ ! -f "$f" ]]; then
    echo 9088
    return
  fi
  local v
  v=$(grep -E '^[[:space:]]*KORUS_WEB_LB_PORT[[:space:]]*=' "$f" | tail -1 | sed -E 's/^[[:space:]]*KORUS_WEB_LB_PORT[[:space:]]*=[[:space:]]*([0-9]+).*/\1/' || true)
  if [[ -n "${v// }" ]]; then
    echo "$v"
  else
    echo 9088
  fi
}

lb="$(read_lb_port)"
ui="http://localhost:${lb}/"

echo ""
echo "=== Korus Messenger (dev) — ссылки и логины ==="
echo "Веб-клиент (UI за lb, стек korus-web):  $ui"
echo "Встроенная админ-консоль API:         http://localhost:8080/admin/"
echo "Health core-api:                        http://localhost:8080/api/v1/health"
echo "Keycloak (консоль IdP, не realm-user): http://localhost:8081  — admin / admin  (KEYCLOAK_ADMIN)"
echo "ws-gateway с профилем web (хост):      ws://localhost:8082/ws"
echo ""
echo "Вход в UI / API (realm avandocmsg, keycloak/avandocmsg-realm.json):"
echo "  admin   / admin"
echo "  csadmin / csadmin"
echo ""
echo "Обычного пользователя без admin в импорте нет — вкладка «Регистрация» в UI или API register."
echo ""
