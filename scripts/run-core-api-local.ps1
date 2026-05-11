# Run core-api on host (JDK 17+). Bootstraps PATH (Docker/Java), optional winget install, docker compose infra when docker exists.
param(
    [switch]$SkipInstallDeps,
    [switch]$NoAutoInfra
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

if ($env:JAVA_HOME -and -not $env:KORUS_JAVA_HOME) {
    $env:KORUS_JAVA_HOME = $env:JAVA_HOME
}

Set-Location $Root

if (-not $SkipInstallDeps) {
    $hasDocker = [bool](Get-Command docker -ErrorAction SilentlyContinue)
    $hasJava = [bool](Get-Command java -ErrorAction SilentlyContinue)
    if (-not $hasDocker -or -not $hasJava) {
        $silent = Join-Path $PSScriptRoot "install-env-silent.ps1"
        if (Test-Path -LiteralPath $silent) {
            Write-Host "Running install-env-silent.ps1 (docker/java missing or not on PATH)..." -ForegroundColor Yellow
            & $silent -Quiet
            if (Test-Path -LiteralPath $Bootstrap) {
                Initialize-KorusDevToolPaths
            }
        }
    }
}

if (-not $NoAutoInfra) {
    $compose = if ($env:KORUS_COMPOSE_DEV_MIN) { $env:KORUS_COMPOSE_DEV_MIN } else { Join-Path $Root "docker\docker-compose.dev-min.yml" }
    if (-not (Test-Path -LiteralPath $compose)) {
        throw "Compose not found: $compose"
    }
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        Write-Host "docker compose up: postgres-hot redis nats minio keycloak..." -ForegroundColor Cyan
        docker compose -f $compose up -d postgres-hot redis nats minio keycloak
        Start-Sleep -Seconds 8
    } else {
        Write-Warning "docker not on PATH: skipping compose. Install Docker Desktop or run .\scripts\dev-infra-up.ps1. Default DB: jdbc:postgresql://localhost:5432/avandocmsg_hot"
    }
}

$env:DB_JDBC_URL = if ($env:DB_JDBC_URL) { $env:DB_JDBC_URL } else { "jdbc:postgresql://localhost:5432/avandocmsg_hot" }
$env:DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "avandocmsg" }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "avandocmsg" }
$env:NATS_URL = if ($env:NATS_URL) { $env:NATS_URL } else { "nats://localhost:4222" }
$env:REDIS_URI = if ($env:REDIS_URI) { $env:REDIS_URI } else { "redis://localhost:6379" }
$env:APP_PORT = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
if (-not $env:KEYCLOAK_ISSUER) { $env:KEYCLOAK_ISSUER = "http://localhost:8081/realms/avandocmsg" }
if (-not $env:KEYCLOAK_JWKS_URL) { $env:KEYCLOAK_JWKS_URL = "http://localhost:8081/realms/avandocmsg/protocol/openid-connect/certs" }
if (-not $env:KEYCLOAK_MASTER_USER) { $env:KEYCLOAK_MASTER_USER = "admin" }
if (-not $env:KEYCLOAK_MASTER_PASSWORD) { $env:KEYCLOAK_MASTER_PASSWORD = "admin" }
if (-not $env:MINIO_ENDPOINT) { $env:MINIO_ENDPOINT = "http://localhost:9000" }
if (-not $env:MINIO_ACCESS_KEY) { $env:MINIO_ACCESS_KEY = "avandocmsg" }
if (-not $env:MINIO_SECRET_KEY) { $env:MINIO_SECRET_KEY = "avandocmsg123" }
if (-not $env:MINIO_BUCKET) { $env:MINIO_BUCKET = "avandocmsg" }

Write-Host "Starting core-api (DB=$($env:DB_JDBC_URL), NATS=$($env:NATS_URL), MinIO=$($env:MINIO_ENDPOINT), JAVA_HOME=$($env:JAVA_HOME), KORUS_DOCKER_EXE=$($env:KORUS_DOCKER_EXE))..." -ForegroundColor Cyan
& .\gradlew.bat :modules:core-api:run --no-daemon
