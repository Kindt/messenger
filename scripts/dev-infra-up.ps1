# Minimal infra for local JVM: PostgreSQL, Redis, NATS, MinIO. Optional Keycloak (-WithKeycloak).
param(
    [switch]$WithKeycloak
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Bootstrap = Join-Path $PSScriptRoot "lib\Bootstrap-DevEnv.ps1"
if (Test-Path -LiteralPath $Bootstrap) {
    . $Bootstrap
    Initialize-KorusDevToolPaths
}
$korusEnv = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (Test-Path -LiteralPath $korusEnv) {
    . $korusEnv
    Set-KorusPathEnvironment -RepoRoot $Root
}
$Compose = if ($env:KORUS_COMPOSE_DEV_MIN) { $env:KORUS_COMPOSE_DEV_MIN } else { Join-Path $Root "docker\docker-compose.dev-min.yml" }

if (-not (Test-Path $Compose)) {
    Write-Error "Compose file not found: $Compose"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "docker not on PATH. Run scripts/run-core-api-local.ps1 once (bootstrap) or install Docker Desktop."
}

if ($WithKeycloak) {
    docker compose -f $Compose up -d postgres-hot redis nats minio keycloak
    $list = "postgres-hot, redis, nats, minio, keycloak"
} else {
    docker compose -f $Compose up -d postgres-hot redis nats minio
    $list = "postgres-hot, redis, nats, minio"
}
Write-Host ""
Write-Host "[OK] Started: $list" -ForegroundColor Green
Write-Host "Next: .\scripts\run-core-api-local.ps1   or   .\gradlew.bat :modules:core-api:run"
Write-Host "Health: curl http://localhost:8080/api/v1/health"
