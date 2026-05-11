# Сборка образов стенда (pull + build). Аналог create-stand.sh под Windows.
# Из корня репозитория: .\scripts\create-stand.ps1 [min|full]
param(
    [Parameter(Position = 0)]
    [ValidateSet("min", "full")]
    [string]$Stand = "min"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$DockerDir = Join-Path $Root "docker"
$MinCompose = Join-Path $DockerDir "docker-compose.dev-min.yml"
$FullCompose = Join-Path $DockerDir "docker-compose.full-server.yml"

Write-Host "=== Creating AvandocMsg stand: $Stand ===" -ForegroundColor Cyan

Push-Location $Root
try {
    switch ($Stand) {
        "min" {
            docker compose -f $MinCompose pull
            docker compose -f $MinCompose build
            Write-Host "Stand 'min' created. Run: .\scripts\start.ps1 min" -ForegroundColor Green
        }
        "full" {
            docker compose -f $FullCompose pull
            docker compose -f $FullCompose build
            Write-Host "Stand 'full' (full-server) created. Run: .\scripts\start.ps1 full" -ForegroundColor Green
        }
    }
}
finally {
    Pop-Location
}
