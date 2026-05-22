#!/usr/bin/env bash
# Подсказки по URL и тестовым учётным данным (локальный dev-стенд).
# Из корня репозитория: ./scripts/dev-ui-hints.sh
set -euo pipefail

LAN_IP=""
SERVER_LAN_IP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      echo "Usage: $0 [--lan-ip <web-host>] [--server-lan-ip <api-host>]"
      echo "  Windows: .\\scripts\\dev-ui-hints.ps1 -LanIp <ip> -ServerLanIp <ip>"
      exit 0
      ;;
    --lan-ip) LAN_IP="${2:-}"; shift 2 ;;
    --server-lan-ip) SERVER_LAN_IP="${2:-}"; shift 2 ;;
    *)
      echo "Unknown argument: $1 (try --help)" >&2
      exit 1
      ;;
  esac
done

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
web_host="${LAN_IP:-localhost}"
api_host="${SERVER_LAN_IP:-localhost}"
ui="http://${web_host}:${lb}/"

echo ""
echo "=== Korus Messenger (dev) — ссылки и логины ==="
echo "Веб-клиент (UI за lb, стек korus-web):  $ui"
if [[ -n "$LAN_IP" || -n "$SERVER_LAN_IP" ]]; then
  echo "  (LAN: web=$web_host api/ws host=$api_host)"
fi
echo "Встроенная админ-консоль API:         http://${api_host}:8080/admin/"
echo "Health core-api:                        http://${api_host}:8080/api/v1/health"
echo "Keycloak (консоль IdP, не realm-user): http://${api_host}:8081  — admin / admin  (KEYCLOAK_ADMIN)"
echo "ws-gateway с профилем web (хост):      ws://${api_host}:8082/ws"
echo ""
echo "Вход в UI / API (realm avandocmsg, keycloak/avandocmsg-realm.json):"
echo "  admin   / admin"
echo "  csadmin / csadmin"
echo ""
echo "Обычного пользователя без admin в импорте нет — вкладка «Регистрация» в UI или API register."
echo ""
echo "Export / compliance (full-server + overlays):"
echo "  core-api metrics:     http://${api_host}:8080/api/v1/metrics/prometheus"
echo "  export-replay:        http://${api_host}:9193/metrics"
echo "  retention:            http://${api_host}:9192/metrics"
echo "  Admin -> Export compliance: seed+file / compliance flow / guide"
echo "  POST /api/v1/admin/export-compliance-prep  { include_file: true }"
echo "  ./scripts/full-stack-up.sh --export-smoke --export-auto-queue"
echo "  ./scripts/smoke-export-compliance-flow.sh --include-file"
echo "  ./scripts/smoke-openapi-export-compliance.sh"
echo "  ./scripts/smoke-export-compliance-pack.sh"
echo "  ./scripts/smoke-export-compliance-stack.sh --auto-queue --down"
echo "  CI: Actions -> Export compliance smoke -> Run workflow"
echo ""
