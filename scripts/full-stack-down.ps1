# Останавливает стек docker/docker-compose.full-server.yml (контейнеры; тома не удаляются).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Root "docker\docker-compose.full-server.yml"

if (-not (Test-Path $Compose)) {
    Write-Error "Не найден $Compose"
}

Write-Host "docker compose -f $Compose down" -ForegroundColor Cyan
Push-Location $Root
try {
    docker compose -f $Compose down
} finally {
    Pop-Location
}

Write-Host "[OK] Стек остановлен." -ForegroundColor Green
