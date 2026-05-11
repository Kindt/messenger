# Start Docker stand (dev-min or full-server). From repo root: .\scripts\start.ps1 [min|full]
# Sets KORUS_* env vars, runs install check / silent install, then docker compose (2 attempts).
# Skip tooling: -SkipEnsure or env SKIP_KORUS_ENSURE=1 (same as start.sh --skip-ensure).
# Help: .\scripts\start.ps1 -Help
param(
    [Parameter(Position = 0)]
    [ValidateSet("min", "full")]
    [string]$Stand = "min",
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\start.ps1 [min|full] [-SkipEnsure]  (default stand: min)"
    Write-Host "  Env SKIP_KORUS_ENSURE=1 skips tooling (same as -SkipEnsure). Linux/macOS: ./scripts/start.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    try {
        Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
    } catch {
        Write-Error "Environment setup failed: $_"
    }
}

$MinCompose = $env:KORUS_COMPOSE_DEV_MIN
$FullCompose = $env:KORUS_COMPOSE_FULL_SERVER

Write-Host "=== Starting AvandocMsg stand: $Stand ===" -ForegroundColor Cyan
Write-Host "KORUS_REPO_ROOT=$($env:KORUS_REPO_ROOT)" -ForegroundColor DarkGray

Push-Location $env:KORUS_REPO_ROOT
try {
    switch ($Stand) {
        "min" {
            Invoke-KorusDockerComposeUp -ComposeFile $MinCompose -Retries 2
            Write-Host "Waiting for services..." -ForegroundColor Yellow
            Start-Sleep -Seconds 5
            Write-Host "Core API: http://localhost:8080/api/v1/health"
            Write-Host "Keycloak: http://localhost:8081 (admin/admin = Keycloak console)"
            Write-Host "Admin UI: http://localhost:8080/admin/  (realm avandocmsg: csadmin/csadmin)"
            Write-Host "Solr:     http://localhost:8983"
            Write-Host "MinIO:    http://localhost:9001 (avandocmsg/avandocmsg123)"
            Write-Host "NATS:     nats://localhost:4222"
            Write-Host "Redis:    redis://localhost:6379"
            Write-Host "PG Hot:   postgres://localhost:5432 (avandocmsg/avandocmsg)"
            Write-Host "PG Arch:  postgres://localhost:5433 (avandocmsg/avandocmsg)"
        }
        "full" {
            Invoke-KorusDockerComposeUp -ComposeFile $FullCompose -Retries 2
            Write-Host "Stand 'full' (full-server) started. WS gateway :8082, retention metrics :9192" -ForegroundColor Green
            Write-Host "Admin UI: http://localhost:8080/admin/"
        }
    }
} finally {
    Pop-Location
}
