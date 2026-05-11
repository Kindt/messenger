# Keycloak + postgres-hot (realm импорт из .\keycloak\). Порт UI/API на хосте: 8081.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Root "docker\docker-compose.dev-min.yml"

docker compose -f $Compose up -d postgres-hot keycloak
Write-Host "[OK] Keycloak: http://localhost:8081  (admin/admin по умолчанию в compose)" -ForegroundColor Green
Write-Host "JWKS для core-api: http://localhost:8081/realms/avandocmsg/protocol/openid-connect/certs"
