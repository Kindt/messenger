# Остановка Docker-стенда и удаление томов. Аналог clean.sh под Windows.
# Из корня репозитория: .\scripts\clean.ps1 [min|full|all]
# По умолчанию: min — docker/docker-compose.dev-min.yml
# full — docker/docker-compose.full-server.yml (полный стек вместо отсутствующего dev-full)
param(
    [Parameter(Position = 0)]
    [ValidateSet("min", "full", "all")]
    [string]$Stand = "min"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$DockerDir = Join-Path $Root "docker"
$MinCompose = Join-Path $DockerDir "docker-compose.dev-min.yml"
$FullCompose = Join-Path $DockerDir "docker-compose.full-server.yml"

Write-Host "=== Cleaning AvandocMsg stand: $Stand ===" -ForegroundColor Cyan

Push-Location $Root
try {
    switch ($Stand) {
        "min" {
            docker compose -f $MinCompose down -v
            Write-Host "Stand 'min' cleaned (volumes removed)" -ForegroundColor Green
        }
        "full" {
            docker compose -f $FullCompose down -v
            Write-Host "Stand 'full' (full-server compose) cleaned (volumes removed)" -ForegroundColor Green
        }
        "all" {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                docker compose -f $MinCompose down -v 2>&1 | Out-Null
                docker compose -f $FullCompose down -v 2>&1 | Out-Null
                docker system prune -f
            } finally {
                $ErrorActionPreference = $prev
            }
            Write-Host "All stands cleaned (dev-min + full-server, prune)" -ForegroundColor Green
        }
    }
}
finally {
    Pop-Location
}
